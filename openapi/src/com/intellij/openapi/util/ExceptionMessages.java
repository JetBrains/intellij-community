/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.util;

import java.io.IOException;
import java.util.HashMap;

/**
 *
 */
public class ExceptionMessages {
  static  final HashMap<Integer, String> ourIOMessages;
  static {
    ourIOMessages = new HashMap<Integer,String>();
    if(SystemInfo.isWindows) {
      ourIOMessages.put(new Integer(1), "Incorrect function.");
      ourIOMessages.put(new Integer(2), "The system cannot find the file specified.");
      ourIOMessages.put(new Integer(3), "The system cannot find the path specified.");
      ourIOMessages.put(new Integer(4), "The system cannot open the file.");
      ourIOMessages.put(new Integer(5), "Access is denied.");
      ourIOMessages.put(new Integer(6), "The handle is invalid.");
      ourIOMessages.put(new Integer(7), "The storage control blocks were destroyed.");
      ourIOMessages.put(new Integer(8), "Not enough storage is available to process this command.");
      ourIOMessages.put(new Integer(9), "The storage control block address is invalid.");
      ourIOMessages.put(new Integer(10), "The environment is incorrect.");
      ourIOMessages.put(new Integer(11), "An attempt was made to load a program with an incorrect format.");
      ourIOMessages.put(new Integer(12), "The access code is invalid.");
      ourIOMessages.put(new Integer(13), "The data is invalid.");
      ourIOMessages.put(new Integer(14), "Not enough storage is available to complete this operation.");
      ourIOMessages.put(new Integer(15), "The system cannot find the drive specified.");
      ourIOMessages.put(new Integer(16), "The directory cannot be removed.");
      ourIOMessages.put(new Integer(17), "The system cannot move the file to a different disk drive.");
      ourIOMessages.put(new Integer(18), "There are no more files.");
      ourIOMessages.put(new Integer(19), "The media is write protected.");
      ourIOMessages.put(new Integer(20), "The system cannot find the device specified.");
      ourIOMessages.put(new Integer(21), "The device is not ready.");
      ourIOMessages.put(new Integer(22), "The device does not recognize the command.");
      ourIOMessages.put(new Integer(23), "Data error (cyclic redundancy check).");
      ourIOMessages.put(new Integer(24), "The program issued a command but the command length is incorrect.");
      ourIOMessages.put(new Integer(25), "The drive cannot locate a specific area or track on the disk.");
      ourIOMessages.put(new Integer(26), "The specified disk or diskette cannot be accessed.");
      ourIOMessages.put(new Integer(27), "The drive cannot find the sector requested.");
    }
  }

  public static String getMessage(IOException exception) {
    String exceptionMessage = exception.getMessage();
    String detailedMessage = null;
    int idx = exceptionMessage.indexOf('=');
    if(idx != -1) {
      int endIdx = idx + 1;
      for(; endIdx < exceptionMessage.length(); endIdx ++) {
        if(!Character.isDigit(exceptionMessage.charAt(endIdx))) break;
      }
      try {
        int errorNumber = Integer.parseInt(exceptionMessage.substring(idx + 1, endIdx));
        detailedMessage = ourIOMessages.get(new Integer(errorNumber));
      }
      catch (NumberFormatException e) {
      }
    }
    StringBuffer buf = new StringBuffer();
    buf.append(exceptionMessage);
    if(detailedMessage != null) {
      buf.append("\n");
      buf.append(detailedMessage);
    }

    return buf.toString();
  }
}
