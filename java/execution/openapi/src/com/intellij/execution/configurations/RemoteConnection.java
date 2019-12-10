// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.configurations;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class RemoteConnection {
  private boolean myUseSockets;
  private boolean myServerMode;

  private String myApplicationHostName;
  private String myApplicationPort;
  private String myDebuggerHostName;
  private String myDebuggerPort;

  public static final String ONTHROW = ",onthrow=<FQ exception class name>";
  public static final String ONUNCAUGHT = ",onuncaught=<y/n>";

  public RemoteConnection(boolean useSockets, String hostName, String address, boolean serverMode) {
    myUseSockets = useSockets;
    myServerMode = serverMode;
    myApplicationHostName = hostName;
    myDebuggerHostName = hostName;
    myApplicationPort = address;
    myDebuggerPort = address;
  }

  public boolean isUseSockets() {
    return myUseSockets;
  }

  public boolean isServerMode() {
    return myServerMode;
  }

  public void setUseSockets(boolean useSockets) {
    myUseSockets = useSockets;
  }

  public void setServerMode(boolean serverMode) {
    myServerMode = serverMode;
  }

  /**
   * @deprecated use {@link #getApplicationHostName()} or {@link #getDebuggerHostName()} instead depending on your needs
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.2")
  public String getHostName() {
    return myApplicationHostName;
  }

  /**
   * @deprecated use {@link #getApplicationPort()} or {@link #getDebuggerPort()} instead depending on your needs
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.2")
  public String getAddress() {
    return myApplicationPort;
  }

  /**
   * @deprecated use {@link #setApplicationHostName(String)} or {@link #setDebuggerHostName(String)} instead depending on your needs
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.2")
  public void setHostName(String hostName) {
    myApplicationHostName = hostName;
    myDebuggerHostName = hostName;
  }

  /**
   * @deprecated use {@link #setApplicationPort(String)} or {@link #setDebuggerPort(String)} instead depending on your needs
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.2")
  public void setAddress(String address) {
    myApplicationPort = address;
    myDebuggerPort = address;
  }

  public String getApplicationHostName() {
    return myApplicationHostName;
  }

  public void setApplicationHostName(String hostName) {
    myApplicationHostName = hostName;
  }

  public String getDebuggerHostName() {
    return myDebuggerHostName;
  }

  public void setDebuggerHostName(String debuggerHostName) {
    myDebuggerHostName = debuggerHostName;
  }

  public String getApplicationPort() {
    return myApplicationPort;
  }

  public void setApplicationPort(String applicationPort) {
    myApplicationPort = applicationPort;
  }

  public String getDebuggerPort() {
    return myDebuggerPort;
  }

  public void setDebuggerPort(String debuggerPort) {
    myDebuggerPort = debuggerPort;
  }

  public String getLaunchCommandLine() {
    final String address = getApplicationPort();
    final boolean shmem = !isUseSockets();
    final boolean serverMode = isServerMode();

    @NonNls String result;
    if (shmem) {
      if (serverMode) {
        result = "-Xdebug -Xrunjdwp:transport=dt_shmem,server=n,address=" +
                 ((address.length() > 0) ? address : "...") + ",suspend=y" + ONTHROW + ONUNCAUGHT;
      }
      else {
        result = "-Xdebug -Xrunjdwp:transport=dt_shmem,server=y,suspend=n,address=" +
                 ((address.length() > 0) ? address : "...");
      }
    }
    else { // socket transport
      int port = StringUtil.parseInt(address, -1);
      if (serverMode) {
        String localHostName = "<host name>:";
        try {
          final InetAddress localAddress = InetAddress.getLocalHost();
          final String name = localAddress.getCanonicalHostName();
          if (name != null) {
            localHostName = name + ":";
          }
        }
        catch (UnknownHostException ignored) {
        }
        result = "-Xdebug -Xrunjdwp:transport=dt_socket,server=n,address=" + localHostName +
                 ((port == -1) ? "<port>" : Integer.toString(port)) + ",suspend=y" + ONTHROW + ONUNCAUGHT;
      }
      else {
        result = "-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=" +
                 ((port == -1) ? "..." : Integer.toString(port));
      }
    }
    return result;
  }
}
