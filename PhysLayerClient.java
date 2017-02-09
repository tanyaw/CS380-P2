import java.net.Socket;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.lang.StringBuilder;

public class PhysLayerClient {
	private static Map<String, Integer> table4B5B = new HashMap<String, Integer>();
	private static int[] receivedBytes = new int[320];
	private static String[] signals = new String[320];
	private static byte[] decodedBytes = new byte[32];
	private static double baseline;
	
	public static void main(String[] args) {
		try(Socket socket = new Socket("codebank.xyz", 38002)) {
			System.out.println("Connected to server.");

			//Create byte streams to communicate to Server
			InputStream is = socket.getInputStream();
			OutputStream os = socket.getOutputStream();
		  
			//Create 4B/5B Lookup Table
			create4B5BTable();
		  
			//Establish baseline value
			baseline = calculateBaseline(is);
		  
			//Get 32 bytes from Input Stream
			getData(is);
			
			//Determine High/Low signals 
			readSignals(320);
			
			//Apply NRZI-backwards
			String NRZIback = decodeNRZI();
			
			//Decode NRZIback using reverse 4B/5B (one byte at a time)
			reverse4B5B(NRZIback);
			
			//Print decoded bytes
			printDecodedData();
			
			//Send decoded array to Server
			for(byte b : decodedBytes) {
	        	os.write(b);
	        }
			  
			//Check Server response
			int check = is.read();
			checkResponse(check);
			  
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.out.println("Disconnected from Server.");
	}
	
	/**
	 * Helper Method - 1. Apply 4B/5B in reverse to every 5bits to create a 4bit value
	 * 				   2. Create a byte value
	 * 				   3. Store to byte array
	 */
	private static void reverse4B5B(String NRZIback) {
		String upperByte = "";
		String lowerByte = "";
		int upperIndex = 0;
		int lowerIndex = 5;
		
		//Read 10 signals each iteration
		for(int i=0; i<32; i++) {
			//UpperIndex handles index ranges: 0-4, 10-14, 20-24, etc.
			//Its upperbound increments by (5 * i)  where i = is any odd value 
			//	 Ex. 5, 15, 25, etc.
			while(upperIndex < (5 * ((i*2) +1))) {	
				upperByte += NRZIback.charAt(upperIndex);
				upperIndex++;
			}
			upperIndex += 5;
			
			//LowerIndex handles index rangex: 5-9, 15-19, 25-29, etc.
			//Its upperbound increments by multiples of 10 
			//	 Ex. 10, 20, 30, etc.
			while(lowerIndex < (10 * (i+1))){ 
				lowerByte += NRZIback.charAt(lowerIndex);
				lowerIndex++;
			}
			lowerIndex += 5;
			
			//Apply 4B/5B Table in reverse
			int upperBits = table4B5B.get(upperByte);
			int lowerBits = table4B5B.get(lowerByte);

			//Concatenate upper/lower 4-bits to create a byte
			decodedBytes[i] = (byte) (((upperBits<<4) | lowerBits) & 0xFF );
			
			//Clear temp strings for next iteration
			upperByte = "";
			lowerByte = "";
		}
	}

	/**
	 * Helper Method - Prints decoded bytes
	 */
	private static void printDecodedData() {
		System.out.print("Received 32 bytes: ");
		for(byte b: decodedBytes) {
			System.out.printf("%02X", b);
		}
		System.out.println();
	}

	/**
	 * Helper Method - Apply NRZI encryption method backwards
	 */
	private static String decodeNRZI() {
		StringBuilder decode = new StringBuilder();
		//Set initial signal in decode string
		if(signals[0].equals("L")) {
			decode.append("0");
		} else {
			decode.append("1");
		}
		
		//Apply NRZI backwards for the remainder of signals
		for(int i=1; i<320; i++) {
			if(signals[i].equals("L")) {
				if(signals[i-1].equals("L")) {
					decode.append("0");	
				} else if(signals[i-1].equals("H")) {
					decode.append("1");
				}
			}
			else if(signals[i].equals("H")) {
				if(signals[i-1].equals("L")) {
					decode.append("1");	
				} else if(signals[i-1].equals("H")) {
					decode.append("0");
				}
			}
		}
		return decode.toString();	
	}

	/**
	 * Helper Method - Get 32 bytes of data from Server
	 */
	private static void getData(InputStream is) throws IOException {
		int value;
		for(int i=0; i < 320; i++) {
			value = (int) is.read();
			receivedBytes[i] = value;
		}
	}

	/**
	 * Helper Method - Loop through 32 bytes
	 * 				   320 signals = 32bytes * 8 bits * 1.25(4B/5B) 
	 */
	private static void readSignals(int numSignals) {
		int value;
		for(int i=0; i < numSignals; i++) {
			value = receivedBytes[i];
		  
			if(value > baseline) {
				signals[i] = "H";
			} else {
			  signals[i] = "L";
			}
		}
	}

	/**
	 * Helper Method - Checks with server if program works
	 */
	private static void checkResponse(int i) {
		  if(i == 1) {
				System.out.println("Response good.");
		  } else {
				System.out.println("Response bad.");
		  }	
	}

	/** 
	 * Helper Method - Calculates baseline by averaging 64 signal preamble
	 */
	private static double calculateBaseline(InputStream is) throws IOException {
		double average = 0;
		for(int i=0; i< 64; i++) {
			double signal = (double) is.read();
			average += signal;
		}
		average = average/64.0;
		System.out.printf("Baseline established from preamble: %.2f", average);
		System.out.println();
		return average; 
	}
	
	/**
	 * Helper Method - Hardcode 4B/5B Table values (in reverse)
	 */
	private static void create4B5BTable() {
		table4B5B.put("11110", 0);
		table4B5B.put("01001", 1);
		table4B5B.put("10100", 2);
		table4B5B.put("10101", 3);
		table4B5B.put("01010", 4);
		table4B5B.put("01011", 5);
		table4B5B.put("01110", 6);
		table4B5B.put("01111", 7);
		table4B5B.put("10010", 8);
		table4B5B.put("10011", 9);
		table4B5B.put("10110", 10);
		table4B5B.put("10111", 11);
		table4B5B.put("11010", 12);
		table4B5B.put("11011", 13);
		table4B5B.put("11100", 14);
		table4B5B.put("11101", 15);
	}
}