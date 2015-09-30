/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
