/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.engine;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.configurations.RemoteConnection;
import com.intellij.execution.configurations.RunProfileState;

import java.util.EventListener;


public interface DebugProcessListener extends EventListener{
  //executed in manager thread
  void connectorIsReady();

  //executed in manager thread
  void paused(SuspendContext suspendContext);

  //executed in manager thread
  void resumed(SuspendContext suspendContext);

  //executed in manager thread
  void processDetached(DebugProcess process);

  //executed in manager thread
  void processAttached(DebugProcess process);

  void attachException(RunProfileState state, ExecutionException exception, RemoteConnection remoteConnection);
}

