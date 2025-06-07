// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

  public abstract @NotNull BreakpointManager getBreakpointManager();

  public abstract @NotNull Collection<DebuggerSession> getSessions();

  public abstract @Nullable DebuggerSession getSession(DebugProcess debugProcess);

  public abstract @NotNull DebuggerContextImpl getContext();

  public abstract @NotNull DebuggerStateManager getContextManager();

  /**
   * @deprecated Use {@link DebuggerManagerListener#TOPIC}
   */
  @Deprecated
  public abstract void addDebuggerManagerListener(@NotNull DebuggerManagerListener debuggerManagerListener);


  /**
   * @deprecated Use {@link DebuggerManagerListener#TOPIC}
   */
  @Deprecated
  public abstract void removeDebuggerManagerListener(@NotNull DebuggerManagerListener debuggerManagerListener);

  public abstract @Nullable DebuggerSession attachVirtualMachine(@NotNull DebugEnvironment environment) throws ExecutionException;
}
