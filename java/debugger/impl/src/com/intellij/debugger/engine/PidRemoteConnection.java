// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine;

import com.intellij.execution.configurations.RemoteConnection;

/**
 * @author egor
 */
public class PidRemoteConnection extends RemoteConnection {
  private final String myPid;

  public PidRemoteConnection(String pid) {
    super(false, null, null, false);
    myPid = pid;
  }

  public String getPid() {
    return myPid;
  }
}
