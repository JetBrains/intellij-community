// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.rt.execution.testFrameworks;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ForkedDebuggerHelper {
  public static final String DEBUG_SOCKET = "-debugSocket";
  private int myDebugPort = -1;
  private Socket myDebugSocket;

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
          e.printStackTrace();
        }
      }
      return port;
    }
    finally {
      serverSocket.close();
    }
  }

  public void setupDebugger(List<String> parameters) throws IOException {
    if (myDebugPort > -1) {
      int debugAddress = findAvailableSocketPort();
      boolean found = false;
      for (int i = 0; i < parameters.size(); i++) {
        String parameter = parameters.get(i);
        final int indexOf = Math.max(parameter.indexOf("transport=dt_socket"), parameter.indexOf("transport=dt_shmem"));
        if (indexOf >= 0) {
          if (debugAddress > -1) {
            parameter = parameter.substring(0, indexOf) + "transport=dt_socket,server=n,suspend=y,address=" + debugAddress;
            parameters.set(i, parameter);
            found = true;
          }
          else {
            parameters.remove(parameter);
          }
          break;
        }
      }
      if (!found) {
        parameters.add("-agentlib:jdwp=transport=dt_socket,server=n,suspend=y,address=" + debugAddress);
      }
      if (debugAddress > -1) {
        Socket socket = getDebugSocket();
        DataOutputStream stream = new DataOutputStream(socket.getOutputStream());
        stream.writeInt(debugAddress);
        int read = socket.getInputStream().read();
      }
    }
  }

  protected Socket getDebugSocket() throws IOException {
    if (myDebugSocket == null) {
      myDebugSocket = new Socket("127.0.0.1", myDebugPort);
    }
    return myDebugSocket;
  }

  public String[] excludeDebugPortFromArgs(String[] args) {
    for (int i = 0; i < args.length; i++) {
      String arg = args[i];
      if (arg.startsWith(DEBUG_SOCKET)) {
        final List<String> list = new ArrayList<String>(Arrays.asList(args));
        list.remove(arg);
        args = list.toArray(new String[0]);
        myDebugPort = Integer.parseInt(arg.substring(DEBUG_SOCKET.length()));
        break;
      }
    }
    return args;
  }

  public void closeDebugSocket() throws IOException {
    if (myDebugSocket != null) myDebugSocket.close();
  }
}