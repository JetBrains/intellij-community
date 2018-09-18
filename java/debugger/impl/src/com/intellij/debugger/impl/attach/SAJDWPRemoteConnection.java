// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.impl.attach;

import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.execution.ExecutionException;
import com.sun.jdi.connect.AttachingConnector;
import com.sun.jdi.connect.Connector;

/**
 * @author egor
 */
public class SAJDWPRemoteConnection extends PidRemoteConnection {
  public SAJDWPRemoteConnection(String pid) {
    super(pid);
  }

  @Override
  public AttachingConnector getConnector() {
    return (AttachingConnector)ConnectorHolder.INSTANCE;
  }

  private static class ConnectorHolder {
    static final Connector INSTANCE;

    static {
      Connector connector = null;
      try {
        connector = DebugProcessImpl.findConnector("com.jetbrains.sa.SAJDWPAttachingConnector");
      }
      catch (ExecutionException ignored) {
      }
      INSTANCE = connector;
    }
  }

  public static boolean isAvailable() {
    return ConnectorHolder.INSTANCE != null;
  }
}
