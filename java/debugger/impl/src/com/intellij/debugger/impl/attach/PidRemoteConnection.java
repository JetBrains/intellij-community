// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.impl.attach;

import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.RemoteConnection;
import com.sun.jdi.connect.Connector;

public class PidRemoteConnection extends RemoteConnection {
  private final String myPid;
  private final boolean myFixedAddress;

  public PidRemoteConnection(String pid) {
    super(false, null, null, false);
    myFixedAddress = false;
    myPid = pid;
  }

  public PidRemoteConnection(String pid, boolean useSockets, String hostName, String address, boolean serverMode) {
    super(useSockets, hostName, address, serverMode);
    myFixedAddress = true;
    myPid = pid;
  }

  public String getPid() {
    return myPid;
  }

  public Connector getConnector(DebugProcessImpl debugProcess) throws ExecutionException {
    assert !myFixedAddress;
    return DebugProcessImpl.findConnector("com.sun.jdi.ProcessAttach");
  }

  public boolean isFixedAddress() {
    return myFixedAddress;
  }
}
