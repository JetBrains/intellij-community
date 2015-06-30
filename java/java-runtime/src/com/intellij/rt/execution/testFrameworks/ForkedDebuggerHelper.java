/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
          System.err.println(e);
        }
      }
      return port;
    }
    finally {
      serverSocket.close();
    }
  }

  public void setupDebugger(List parameters) throws IOException {
    if (myDebugPort > -1) {
      int debugAddress = findAvailableSocketPort();
      boolean found = false;
      for (int i = 0; i < parameters.size(); i++) {
        String parameter = (String)parameters.get(i);
        final String debuggerParam = "transport=dt_socket";
        final int indexOf = parameter.indexOf(debuggerParam);
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
        final List list = new ArrayList(Arrays.asList(args));
        list.remove(arg);
        args = (String[])list.toArray(new String[list.size()]);
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