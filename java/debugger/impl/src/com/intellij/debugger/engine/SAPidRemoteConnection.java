// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine;

import com.intellij.execution.configurations.RemoteConnection;

/**
 * @author egor
 */
public class SAPidRemoteConnection extends RemoteConnection {
  private final String myPid;
  private final String mySAJarPath;

  public SAPidRemoteConnection(String pid, String saJarPath) {
    super(false, null, null, false);
    myPid = pid;
    mySAJarPath = saJarPath;
  }

  public String getPid() {
    return myPid;
  }

  public String getSAJarPath() {
    return mySAJarPath;
  }
}
