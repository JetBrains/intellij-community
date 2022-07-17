// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.configurations;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NonNls;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class RemoteConnection {
  private boolean myUseSockets;
  private boolean myServerMode;

  private String myApplicationHostName;
  private String myApplicationAddress;
  private String myDebuggerHostName;
  private String myDebuggerAddress;

  public static final String ONTHROW = ",onthrow=<FQ exception class name>";
  public static final String ONUNCAUGHT = ",onuncaught=<y/n>";

  public RemoteConnection(boolean useSockets, String hostName, String address, boolean serverMode) {
    myUseSockets = useSockets;
    myServerMode = serverMode;
    myApplicationHostName = hostName;
    myDebuggerHostName = hostName;
    myApplicationAddress = address;
    myDebuggerAddress = address;
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
  @Deprecated(forRemoval = true)
  public String getHostName() {
    return myApplicationHostName;
  }

  /**
   * @deprecated use {@link #getApplicationAddress()} or {@link #getDebuggerAddress()} instead depending on your needs
   */
  @Deprecated(forRemoval = true)
  public String getAddress() {
    return myApplicationAddress;
  }

  /**
   * @deprecated use {@link #setApplicationHostName(String)} or {@link #setDebuggerHostName(String)} instead depending on your needs
   */
  @Deprecated(forRemoval = true)
  public void setHostName(String hostName) {
    myApplicationHostName = hostName;
    myDebuggerHostName = hostName;
  }

  /**
   * @deprecated use {@link #setApplicationAddress(String)} or {@link #setDebuggerAddress(String)} instead depending on your needs
   */
  @Deprecated(forRemoval = true)
  public void setAddress(String address) {
    myApplicationAddress = address;
    myDebuggerAddress = address;
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

  public String getApplicationAddress() {
    return myApplicationAddress;
  }

  public void setApplicationAddress(String applicationAddress) {
    myApplicationAddress = applicationAddress;
  }

  public String getDebuggerAddress() {
    return myDebuggerAddress;
  }

  public void setDebuggerAddress(String debuggerAddress) {
    myDebuggerAddress = debuggerAddress;
  }

  public String getLaunchCommandLine() {
    final String address = getApplicationAddress();
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
