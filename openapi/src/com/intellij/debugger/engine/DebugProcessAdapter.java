/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.engine;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.configurations.RemoteConnection;
import com.intellij.execution.configurations.RunProfileState;

public class DebugProcessAdapter implements DebugProcessListener{
  //executed in manager thread
  public void connectorIsReady() {
  }

  //executed in manager thread
  public void paused(SuspendContext suspendContext) {

  }

  //executed in manager thread
  public void resumed(SuspendContext suspendContext) {

  }

  //executed in manager thread
  public void processDetached(DebugProcess process) {

  }

  //executed in manager thread
  public void processAttached(DebugProcess process) {

  }

  public void attachException(RunProfileState state, ExecutionException exception, RemoteConnection remoteConnection) {

  }
}
