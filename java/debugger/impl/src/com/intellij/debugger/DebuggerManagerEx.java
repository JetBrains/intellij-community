// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger;

import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerManagerListener;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.impl.DebuggerStateManager;
import com.intellij.debugger.ui.breakpoints.BreakpointManager;
import com.intellij.execution.ExecutionException;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public abstract class DebuggerManagerEx extends DebuggerManager {
  public static DebuggerManagerEx getInstanceEx(Project project) {
    return (DebuggerManagerEx)DebuggerManager.getInstance(project);
  }

  @NotNull
  public abstract BreakpointManager getBreakpointManager();

  @NotNull
  public abstract Collection<DebuggerSession> getSessions();

  @Nullable
  public abstract DebuggerSession getSession(DebugProcess debugProcess);

  @NotNull
  public abstract DebuggerContextImpl getContext();

  @NotNull
  public abstract DebuggerStateManager getContextManager();

  public abstract void addDebuggerManagerListener(DebuggerManagerListener debuggerManagerListener);

  public abstract void removeDebuggerManagerListener(DebuggerManagerListener debuggerManagerListener);

  @Nullable
  public abstract DebuggerSession attachVirtualMachine(@NotNull DebugEnvironment environment) throws ExecutionException;
}
