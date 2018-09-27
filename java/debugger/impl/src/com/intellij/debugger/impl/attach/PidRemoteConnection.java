// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.impl.attach;

import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.RemoteConnection;
import com.sun.jdi.connect.Connector;

/**
 * @author egor
 */
public class PidRemoteConnection extends RemoteConnection {
  private final String myPid;

  public PidRemoteConnection(String pid) {
    this(pid, false);
  }

  PidRemoteConnection(String pid, boolean serverMode) {
    super(false, null, null, serverMode);
    myPid = pid;
  }

  public String getPid() {
    return myPid;
  }

  public Connector getConnector(DebugProcessImpl debugProcess) throws ExecutionException {
    return DebugProcessImpl.findConnector("com.sun.jdi.ProcessAttach");
  }
}
