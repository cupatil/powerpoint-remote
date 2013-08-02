package de.predefined.powerpointremote;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.io.*;
import java.net.Socket;


/**
 * Created by Julius on 18.05.13.
 */
public class ConnectionManager extends Thread {
	private Socket connection;
	private DataOutputStream out;
	private DataInputStream in;
	private String hostName;
	private String key;
	private int port;
	private MainActivity runningOn;
	private Slide mSlide;

	/**
	 * Create a new ConnectionManager.
	 * 
	 * @param host
	 *            The servers hostname
	 * @param port
	 *            The servers port
	 * @param key
	 *            The pairing key, entered by the user
	 * @param runningOn
	 *            the instance of our MainActivity that created this Thread
	 */
	public ConnectionManager(String host, int port, String key,
			MainActivity runningOn) {
		this.key = key;
		this.hostName = host;
		this.port = port;
		this.runningOn = runningOn;
		this.mSlide = new Slide();
	}

	/**
	 * Tries to open a connection for our PPTREMOTE-Protocol
	 * 
	 * @param hostName
	 *            The servers hostname
	 * @param key
	 *            The pairing key, entered by the user
	 * @throws WrongPairingCodeException
	 *             Is thrown, when the pairing code is wrong
	 */
	private void connectTo(String hostName, String key)
			throws WrongPairingCodeException {
		try {
			connection = new Socket(hostName, port);
			out = new DataOutputStream(connection.getOutputStream());
			in = new DataInputStream(connection.getInputStream());
		} catch (Exception e) {
			e.printStackTrace();
		}
		// main loop
		authenticate(key);
		while (!connection.isClosed()) {
			try {
				if (in.available() > 0) {
					byte read = in.readByte();
					if (read == 100)
						// Ping
						System.out.print("PING received");
					else if (read == 0) {
						// answer to authentication-request
						boolean answer = in.readBoolean();
						if (!answer) {
							throw new WrongPairingCodeException();
						}
						Log.i("PPTREMOTE", "Successfully connected.");
					} else if (read == 5) {
						int length = this.receiveInt();
						byte[] notesArr = new byte[length];
						in.read(notesArr);
						final String notes = new String(notesArr, "UTF-8");
						this.mSlide.setNotes(notes);
						runningOn.runOnUiThread(new Runnable() {
							@Override
							public void run() {
								runningOn.onNewSlideReceived(mSlide);
							}
						});
					} else if (read == 6) {
						// new image
						int length = this.receiveInt();
						byte[] temp = new byte[length];
						in.read(temp);
						final Bitmap slide = BitmapFactory.decodeByteArray(
								temp, 0, temp.length);
						this.mSlide.setCurrentView(slide);
						runningOn.runOnUiThread(new Runnable() {
							@Override
							public void run() {
								runningOn.onImageChanged(mSlide);
							}
						});

					} else if (read == 2) {
						runningOn.runOnUiThread(new Runnable() {
							@Override
							public void run() {
								runningOn.onPresentationEnded();
							}
						});
					}
				} else {
					try {
						Thread.sleep(50);
					} catch (InterruptedException e) {
						Log.i("PPTREMOTE", e.getMessage());
					}
				}
			} catch (IOException e) {
				Log.i("PPTREMOTE", e.getMessage());
			}
		}

	}

	/**
	 * closes the connection
	 */
	public void disconnect() {
		try {
			this.connection.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * The authentication request
	 * 
	 * @param key
	 *            pairing code, entered by the user
	 */
	private void authenticate(String key) {
		try {
			out.writeByte(0);
			byte[] arbytes = key.getBytes("UTF-8");
			out.write(this.encodeInt(arbytes.length));
			out.write(arbytes);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Send a request to display the next slide
	 */
	public void nextSlide() {
		try {
			out.writeByte(3);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Send a request to display the previous slide
	 */
	public void previousSlide() {
		try {
			out.writeByte(4);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Send a request to start the presentation
	 */
	public void startPresentation() {
		try {
			out.writeByte(1);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Send a request to stop the presentation NOT IMPLEMENTED YET ON SERVER
	 * SIDE
	 */
	public void stopPresentation() {
		try {
			out.writeByte(2);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * starts the whole connection-process
	 */
	@Override
	public void run() {
		try {
			connectTo(hostName, key);
		} catch (WrongPairingCodeException e) {
			runningOn.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					ConnectionManager.this.runningOn
							.authenticateProcedure(true);
				}
			});

		}
	}
	/**
	 * Gets an Integer from a byte array.
	 * @param arr
	 * 		The byte Array.
	 * @return
	 * 		The integer.
	 */
	private int getIntFromBytes(byte[] arr) {
		int result = 0;
		for (int i = 0; i < arr.length; i++) {
			if (arr[i] == 1)
				result += Math.pow(2, i);
		}
		Log.i("PPTREMOTE", "Received int: " + result);
		return result;
	}
	/**
	 * Encodes an integer value to a byte array.
	 * @param value
	 * 		The integer value.
	 * @return
	 * 		The byte array.
	 */
	private byte[] encodeInt(int value) {
		byte[] intBuffer = new byte[32];

		for (int i = 0; i < intBuffer.length; i++) {
			int FLAG = (1 << i);
			boolean isSet = (value & FLAG) == FLAG;
			intBuffer[i] = (byte) (isSet ? 1 : 0);
		}

		return intBuffer;
	}
	/**
	 * Receives an integer from the server.
	 * @return 
	 * 		the integer received from the server.
	 * @throws IOException
	 * 		from DataInputStram.readbyte()
	 */
	private int receiveInt() throws IOException {
		byte[] intBuffer = new byte[32];

		for (int i = 0; i < intBuffer.length; i++) {
			intBuffer[i] = this.in.readByte();
		}

		int i = this.getIntFromBytes(intBuffer);
		System.out.println("INT: " + i);

		return i;
	}

}
