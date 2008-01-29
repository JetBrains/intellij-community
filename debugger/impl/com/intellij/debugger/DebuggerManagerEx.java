package com.intellij.debugger;

import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerManagerListener;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.impl.DebuggerStateManager;
import com.intellij.debugger.ui.breakpoints.BreakpointManager;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.ModuleRunProfile;
import com.intellij.execution.configurations.RemoteConnection;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.openapi.project.Project;

import java.util.Collection;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public abstract class DebuggerManagerEx extends DebuggerManager {
  public static DebuggerManagerEx getInstanceEx(Project project) {
    return (DebuggerManagerEx)DebuggerManager.getInstance(project);
  }
  public abstract BreakpointManager  getBreakpointManager();

  public abstract Collection<DebuggerSession> getSessions();
  public abstract DebuggerSession getSession(DebugProcess debugProcess);

  public abstract DebuggerContextImpl getContext();
  public abstract DebuggerStateManager getContextManager();

  public abstract void addDebuggerManagerListener(DebuggerManagerListener debuggerManagerListener);
  public abstract void removeDebuggerManagerListener(DebuggerManagerListener debuggerManagerListener);

  public abstract DebuggerSession attachVirtualMachine(ProgramRunner runner,
                                                       ModuleRunProfile profile,
                                                       RunProfileState state,
                                                       RemoteConnection connection,
                                                       boolean pollConnection
  ) throws ExecutionException;

}
