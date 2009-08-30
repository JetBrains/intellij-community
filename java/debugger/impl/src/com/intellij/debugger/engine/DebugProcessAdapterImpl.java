/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.engine;

import com.intellij.debugger.engine.DebugProcess;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.RemoteConnection;
import com.intellij.execution.configurations.RunProfileState;

/**
 * Created by IntelliJ IDEA.
 * User: lex
 * Date: Feb 26, 2004
 * Time: 4:11:18 PM
 * To change this template use File | Settings | File Templates.
 */
public class DebugProcessAdapterImpl implements DebugProcessListener {
  //executed in manager thread
  public final void paused(SuspendContext suspendContext) {
    paused(((SuspendContextImpl)suspendContext));
  }

  //executed in manager thread
  public final void resumed(SuspendContext suspendContext) {
    resumed(((SuspendContextImpl)suspendContext));
  }

  //executed in manager thread
  public final void processDetached(DebugProcess process, boolean closedByUser) {
    processDetached(((DebugProcessImpl)process), closedByUser);
  }

  //executed in manager thread
  public final void processAttached(DebugProcess process) {
    processAttached(((DebugProcessImpl)process));
  }

  //executed in manager thread
  public void connectorIsReady() {
  }

  public void paused(SuspendContextImpl suspendContext) {
    //To change body of implemented methods use File | Settings | File Templates.
  }

  //executed in manager thread
  public void resumed(SuspendContextImpl suspendContext) {
    //To change body of implemented methods use File | Settings | File Templates.
  }

  //executed in manager thread
  public void processDetached(DebugProcessImpl process, boolean closedByUser) {
    //To change body of implemented methods use File | Settings | File Templates.
  }

  //executed in manager thread
  public void processAttached(DebugProcessImpl process) {
    //To change body of implemented methods use File | Settings | File Templates.
  }

  public void attachException(RunProfileState state, ExecutionException exception, RemoteConnection remoteConnection) {
  }
}
