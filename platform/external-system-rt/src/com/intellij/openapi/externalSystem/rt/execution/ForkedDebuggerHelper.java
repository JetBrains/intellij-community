// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.rt.execution;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * @author Vladislav.Soroka
 */
public final class ForkedDebuggerHelper {

  public static final String JVM_DEBUG_SETUP_PREFIX = "-agentlib:jdwp=transport=dt_socket,server=n,suspend=y,address=";
  public static final String DEBUG_FORK_SOCKET_PARAM = "-forkSocket";

  public static final String DEBUG_SERVER_PORT_KEY = "DEBUG_SERVER_PORT";
  public static final String PARAMETERS_SEPARATOR = ";";

  public static final String FINISH_PARAMS = "FINISH_PARAMS";
  public static final String DISPATCH_PORT_SYS_PROP = "idea.debugger.dispatch.port";

  // returns port at which debugger is supposed to communicate with debuggee process
  public static int setupDebugger(String debuggerId, String processName, String processParameters) {
    return setupDebugger(debuggerId, processName, processParameters, getPortFromProperty());
  }

  public static int setupDebugger(String debuggerId, String processName, String processParameters, int dispatchPort) {
    int port = 0;
    try {
      port = findAvailableSocketPort();
      processParameters = (processParameters == null || processParameters.isEmpty()) ? "" : processParameters + PARAMETERS_SEPARATOR;
      processParameters = processParameters + DEBUG_SERVER_PORT_KEY + "=" + port;
      send(debuggerId, processName, processParameters, dispatchPort);
    }
    catch (IOException e) {
      //noinspection CallToPrintStackTrace
      e.printStackTrace();
    }
    return port;
  }

  public static void signalizeFinish(String debuggerId, String processName) {
    signalizeFinish(debuggerId, processName, getPortFromProperty());
  }

  public static void signalizeFinish(String debuggerId, String processName, int dispatchPort) {
    try {
      send(debuggerId, processName, FINISH_PARAMS, dispatchPort);
    }
    catch (IOException e) {
      //noinspection CallToPrintStackTrace
      e.printStackTrace();
    }
  }

  private static void send(String debuggerId, String processName, String processParameters, int dispatchPort) throws IOException {
    Socket socket = new Socket("127.0.0.1", dispatchPort);
    try {
      DataOutputStream stream = new DataOutputStream(socket.getOutputStream());
      try {
        stream.writeUTF(debuggerId);
        stream.writeUTF(processName);
        stream.writeUTF(processParameters);
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

      if (port <= -1) {
        throw new IOException("Failed to find available port");
      }

      return port;
    }
    finally {
      serverSocket.close();
    }
  }

  private static int getPortFromProperty() {
    String property = System.getProperty(DISPATCH_PORT_SYS_PROP);
    try {
      if (property == null || property.trim().isEmpty()) {
        throw new IllegalStateException("System property '" + DISPATCH_PORT_SYS_PROP + "' is not set");
      }
      return Integer.parseInt(property);
    } catch (NumberFormatException e) {
      throw new IllegalStateException("System property '" + DISPATCH_PORT_SYS_PROP + "' has invalid value: " + property, e);
    }
  }
}
