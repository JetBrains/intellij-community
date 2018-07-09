// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.rt.execution;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * @author Vladislav.Soroka
 */
public class ForkedDebuggerHelper {

  public static final String DEBUG_SETUP_PREFIX = "-agentlib:jdwp=transport=dt_socket,server=n,suspend=y,address=";
  public static final String DEBUG_FORK_SOCKET_PARAM = "-forkSocket";

  public static String setupDebugger(String processName, int debugPort) {
    String setup = "";
    try {
      if (debugPort > -1) {
        int debugAddress = findAvailableSocketPort();
        if (debugAddress > -1) {
          setup = DEBUG_SETUP_PREFIX + debugAddress;
          send(debugAddress, processName, debugPort);
        }
      }
    }
    catch (Exception e) {
      //noinspection CallToPrintStackTrace
      e.printStackTrace();
    }
    return setup;
  }

  public static void processFinished(String processName, int debugPort) {
    send(0, processName, debugPort);
  }

  private static void send(int signal, String processName, int debugPort) {
    try {
      Socket socket = new Socket("127.0.0.1", debugPort);
      try {
        DataOutputStream stream = new DataOutputStream(socket.getOutputStream());
        try {
          stream.writeInt(signal);
          stream.writeUTF(processName);
          // wait for the signal handling
          int read = socket.getInputStream().read();
        }
        finally {
          stream.close();
        }
      }
      finally {
        socket.close();
      }
    }
    catch (Exception ignore) {
    }
  }

  // copied from NetUtils
  protected static int findAvailableSocketPort() throws IOException {
    final ServerSocket serverSocket = new ServerSocket(0);
    try {
      int port = serverSocket.getLocalPort();
      // workaround for linux : calling close() immediately after opening socket
      // may result that socket is not closed
      //noinspection SynchronizationOnLocalVariableOrMethodParameter
      synchronized (serverSocket) {
        try {
          //noinspection WaitNotInLoop
          serverSocket.wait(1);
        }
        catch (InterruptedException e) {
          System.err.println(e);
        }
      }
      return port;
    }
    finally {
      serverSocket.close();
    }
  }
}
