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
package com.intellij.execution.configurations;

import org.jetbrains.annotations.NonNls;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class RemoteConnection {
  private boolean myUseSockets;
  private boolean myServerMode;
  private String myHostName;
  private String myAddress;
  @NonNls public static final String ONTHROW = ",onthrow=<FQ exception class name>";
  @NonNls public static final String ONUNCAUGHT = ",onuncaught=<y/n>";

  public RemoteConnection(boolean useSockets, String hostName, String address, boolean serverMode) {
    myUseSockets = useSockets;
    myServerMode = serverMode;
    myHostName = hostName;
    myAddress = address;
  }

  public boolean isUseSockets() {
    return myUseSockets;
  }

  public boolean isServerMode() {
    return myServerMode;
  }

  public String getHostName() {
    return myHostName;
  }

  public String getAddress() {
    return myAddress;
  }


  public void setUseSockets(boolean useSockets) {
    myUseSockets = useSockets;
  }

  public void setServerMode(boolean serverMode) {
    myServerMode = serverMode;
  }

  public void setHostName(String hostName) {
    myHostName = hostName;
  }

  public void setAddress(String address) {
    myAddress = address;
  }

  public String getLaunchCommandLine() {
    final String address = getAddress();
    final boolean shmem = !isUseSockets();
    final boolean serverMode = isServerMode();

    @NonNls String result;
    if (shmem) {
      if (serverMode) {
        result = "-Xdebug -Xrunjdwp:transport=dt_shmem,server=n,address=" +
                 ((address.length() > 0)? address : "...") +
                 ONTHROW + ",suspend=y" + ONUNCAUGHT;
      }
      else {
        result = "-Xdebug -Xrunjdwp:transport=dt_shmem,server=y,suspend=n,address=" +
                 ((address.length() > 0)? address : "...");
      }
    }
    else { // socket transport
      int p = -1;
      try {
        p = Integer.parseInt(address);
      }
      catch (NumberFormatException e) {
      }
      if (serverMode) {
        String localHostName = "<host name>:";
        try {
          final InetAddress localAddress = InetAddress.getLocalHost();
          final String name = localAddress.getCanonicalHostName();
          if (name != null) {
            localHostName = name + ":";
          }
        }
        catch (UnknownHostException e) {
        }
        result = "-Xdebug -Xrunjdwp:transport=dt_socket,server=n,address=" + localHostName +
                 ((p == -1)? "<port>" : Integer.toString(p)) +
                 ONTHROW + ",suspend=y" + ONUNCAUGHT;
      }
      else {
        result = "-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=" +
                 ((p == -1)? "..." : Integer.toString(p));
      }
    }
    return result;
  }
}
