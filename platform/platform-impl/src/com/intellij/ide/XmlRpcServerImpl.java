/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.ide;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import org.apache.xmlrpc.IdeaAwareWebServer;
import org.apache.xmlrpc.IdeaAwareXmlRpcServer;
import org.apache.xmlrpc.WebServer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.ServerSocket;

/**
 * @author mike
 */
public class XmlRpcServerImpl implements XmlRpcServer, ApplicationComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.XmlRpcServerImpl");
  private static final int FIRST_PORT_NUMBER = 63342;
  private static final int PORTS_COUNT = 20;
  public static int detectedPortNumber = -1;
  private WebServer myWebServer;
  @NonNls private static final String PROPERTY_RPC_PORT = "rpc.port";

  @NotNull
  @NonNls
  public String getComponentName() {
    return "XmlRpcServer";
  }

  public void initComponent() {
    if (ApplicationManager.getApplication().isUnitTestMode() || !checkPort()) return;
    final Thread thread = Thread.currentThread();
    final int currentPriority = thread.getPriority();
    try {
      thread.setPriority(Thread.NORM_PRIORITY - 2);
      myWebServer = new IdeaAwareWebServer(getPortNumber(), null, new IdeaAwareXmlRpcServer());
      myWebServer.start();
    }
    catch (Exception e) {
      LOG.error(e);
      myWebServer = null;
    }
    finally {
      thread.setPriority(currentPriority);
    }
  }

  public int getPortNumber() {
    return detectedPortNumber == -1 ? getDefaultPort() : detectedPortNumber;
  }

  private static int getDefaultPort() {
    if (System.getProperty(PROPERTY_RPC_PORT) != null) return Integer.parseInt(System.getProperty(PROPERTY_RPC_PORT));
    return FIRST_PORT_NUMBER;
  }

  private static boolean checkPort() {
    ServerSocket socket = null;
    try {
      final int firstPort = getDefaultPort();
      for (int i = 0; i < PORTS_COUNT; i++) {
        int port = firstPort + i;
        try {
          socket = new ServerSocket(port);
          detectedPortNumber = port;
          return true;
        }
        catch (IOException ignored) {
        }
      }

      try {
        // try any port
        socket = new ServerSocket(0);
        detectedPortNumber = socket.getLocalPort();
        return true;
      }
      catch (IOException ignored) {
      }
    }
    finally {
      if (socket != null) {
        try {
          socket.close();
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
    }
    return false;
  }

  public void disposeComponent() {
    if (myWebServer != null) {
      myWebServer.shutdown();
    }
  }

  public void addHandler(String name, Object handler) {
    if (myWebServer != null) {
      myWebServer.addHandler(name, handler);
    }
    else {
      LOG.info("Handler not registered because XML-RPC server is not running");
    }
  }

  public void removeHandler(String name) {
    if (myWebServer != null) {
      myWebServer.removeHandler(name);
    }
  }
}
