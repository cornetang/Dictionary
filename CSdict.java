import java.lang.System;
import java.net.*;
import java.io.*;

//
// This is an implementation of a simplified version of a command 
// line dictionary client. The program takes no arguments.
//


public class CSdict
{
    static final int MAX_LEN = 255;
    static final int PERMITTED_ARGUMENT_COUNT = 1;
    static Boolean debugOn = false;
    static Socket dictSocket = new Socket();
    static PrintWriter dictout = null;
    static BufferedReader dictin = null;
    static String dict = "*";
    static String word = "";


    public static void main(String [] args)
    {
    byte cmdString[] = new byte[MAX_LEN];
    
    if (args.length == PERMITTED_ARGUMENT_COUNT) {
        debugOn = args[0].equals("-d");
        if (debugOn) {
        System.out.println("Debugging output enabled");
        } else {
        System.out.println("997 Invalid command line option - Only -d is allowed");
        return;
            } 
    } else if (args.length > PERMITTED_ARGUMENT_COUNT) {
        System.out.println("996 Too many command line options - Only -d is allowed");
        return;
    }
    
    try {

        for (int len=1; len>0;) {
        System.out.print("csdict> ");
        
        len = System.in.read(cmdString);
        String in = new String(cmdString);
        String[] inArgs = in.trim().split("[\\s]+", 0);
        
        if (inArgs.length <= 0) continue; //ignore empty input
        if (len<=1) continue; //ignore empty line
        if (inArgs[0].equals("")) continue; //to fix bug where .split returns [""] when in.trim() is empty string
        if (inArgs[0].substring(0,1).equals("#")) continue; //ignore lines beginning with #

        // parse command
        String command = inArgs[0].trim().toLowerCase();

        switch (command) {
        case "quit": // handle "quit" command
            if (dictSocket.isConnected()) {
                messageToServer("quit");
            }
            len = 0;
            if (inArgs.length==1) System.exit(0);
            System.out.println("901 Incorrect number of arguments.");
            break;
        case "open": // handle "open SERVER PORT" comment
            if (dictSocket.isConnected()) {
                System.out.println("903 Supplied command not expected at this time. There is already an open connection.");
                break;
            }
            if (inArgs.length>3 || inArgs.length<2) {
                System.out.println("901 Incorrect number of arguments.");
                break;  
            }
            String hostName = inArgs[1];
            int portNumber = 2628;

            if (inArgs.length==3) {
                try {
                    portNumber = Integer.parseInt(inArgs[2]);
                    if (portNumber<0 || portNumber>65535) throw new NumberFormatException();
                } catch (NumberFormatException e) {
                    System.out.println("902 Invalid argument. Please choose a valid port number or leave it blank."); //if portNumber isn't an int or isn't in valid range
                    break;
                }
            }

            try {
                dictSocket = new Socket(hostName, portNumber);
                if (dictSocket.isConnected()) {
                    dictout = new PrintWriter(dictSocket.getOutputStream(), true);
                    dictin = new BufferedReader(new InputStreamReader(dictSocket.getInputStream()));
                    dict = "*";
                    String connectresult = dictin.readLine();
                    if (debugOn) System.out.println("<-- "+connectresult);
                } else throw new IOException();
            } catch (UnknownHostException e) {
                System.out.println("902 Invalid argument. The hostname is not valid.");
            } catch (IOException e) {
                System.out.println("920 Control connection to "+hostName+" on port "+Integer.toString(portNumber)+" failed to open.");
            }
            break;
        case "close": // handle "close" command
            if (!isCommandValid(inArgs.length, 1)) break;
            dictSocket.shutdownInput();
            dictSocket.shutdownOutput();
            dictSocket.close();
            if (dictSocket.isClosed()) {
                dictSocket = new Socket();
                dictin = null;
                dictout = null;
            } else {
                System.out.println("999 Processing error. Didn't successfully disconnect.");
            }
            break;
        case "dict": // handle "dict" command
            if (!isCommandValid(inArgs.length, 1)) break;
            messageToServer("show db");
            printResults(command);
            break;
        case "set": // handle "set DICTIONARY" command
            if (!isCommandValid(inArgs.length, 2)) break;
            dict = inArgs[1];
            break;
        case "currdict": // handle "currdict" command
            if (!isCommandValid(inArgs.length, 1)) break;
            System.out.println(dict);
            break;
        case "define": // handle "define WORD" command
            if (!isCommandValid(inArgs.length, 2)) break;
            word = inArgs[1];
            messageToServer("define " + dict + " " + word);
            printResults(command);
            break;
        case "match": // handle "match WORD" command
            if (!isCommandValid(inArgs.length, 2)) break;
            messageToServer("match " + dict + " exact " + inArgs[1]);
            printResults(command);
            break;
        case "prefixmatch": // handle "prefixmatch WORD" command
            if (!isCommandValid(inArgs.length, 2)) break;
            messageToServer("match " + dict + " prefix " + inArgs[1]);
            printResults(command);
            break;
        default:
            System.out.println("900 Invalid command.");
            break;
        }

        cmdString = new byte[MAX_LEN]; //reset
        }
    } catch (IOException exception) {
        System.err.println("998 Input error while reading commands, terminating.");
    }
    }

    // return false when connection is not made or when the number of arguments are not legal
    private static Boolean isCommandValid(int argsLength, int expectedArgs) {
        if (!dictSocket.isConnected()){
            System.out.println("903 Supplied command not expected at this time. There are no open connections.");
            return false;
        }
        else if (argsLength != expectedArgs) {
            System.out.println("901 Incorrect number of arguments.");
            return false;
        } else {
            return true;
        }
    }

    // send command to server
    private static void messageToServer(String message) {
        dictout.println(message);
        if (debugOn) System.out.println("--> "+message);
    }

    // print result from server
    private static void printResults(String command) {
        try {
        String result;
        String status;
        while ((result = dictin.readLine()) != null) {
            if (result.length()>=3) {
                status = result.substring(0,3);
            } else {
                status = "";
            }
            // 110 n databases present - text follows
            // 151 word database name - text follows
            // 152 n matches found - text follows
            if (status.equals("110") || status.equals("152") || status.equals("150")) {
                if (debugOn) System.out.println("<-- "+result);
                continue;
            }
            // 151 word database name - text follows
            // printing dictinoary database
            if (status.equals("151")) {
                if (debugOn) {
                    System.out.println("<-- "+result);
                } else {
                    System.out.println("@ "+result.substring(7+word.length()));    
                }
                continue;
            }
            // General Status Responses
            if (status.equals("500") || status.equals("501") || status.equals("502") || status.equals("503") || status.equals("420") || status.equals("421")){
                if (debugOn) System.out.println("<-- "+result);
                System.out.println("925 Control connection I/O error, closing control connection.");
                dictSocket.shutdownInput();
                dictSocket.shutdownOutput();
                dictSocket.close();
                if (dictSocket.isClosed()) {
                    dictSocket = new Socket();
                    dictin = null;
                    dictout = null;
                }
                break;
            }
            // 550 Invalid database, use "SHOW DB" for list of databases
            if (status.equals("550")){
                if (debugOn) System.out.println("<-- "+result);
                System.out.println("930 Dictionary does not exist.");
                break;
            }
            // 552 No match
            // printing error messages for no matches 
            if (status.equals("552")){
                if (debugOn) System.out.println("<-- "+result);
                switch(command) {
                    case "match":
                        System.out.println("****No matching word(s) found****");
                        break;
                    case "prefixmatch":
                        System.out.println("****No prefix matches found****");
                        break;
                    case "define":
                        System.out.println("**No definition found**");
                        dictout.println("match * exact " + word);
                        if (debugOn) System.out.println("--> "+"match * exact " + word);
                        printResults("definematch"); // carry out match search for define search with no results
                        break;
                    case "definematch":
                        System.out.println("***No dictionaries have a definition for this word***");
                        break;
                }
                break;
            }
            // 250 ok
            if (status.equals("250")) {
                if (debugOn) System.out.println("<-- "+result);
                break;
            }
            System.out.println(result);
        }
        } catch (IOException exception) {
            System.err.println("998 Input error while reading commands, terminating.");
        }
    }
}