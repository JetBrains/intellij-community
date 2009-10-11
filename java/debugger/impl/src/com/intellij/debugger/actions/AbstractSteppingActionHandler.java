/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.debugger.actions;

import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.impl.actions.DebuggerActionHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public abstract class AbstractSteppingActionHandler extends DebuggerActionHandler {

  @Nullable
  protected static DebuggerSession getSession(@NotNull Project project) {
    final DebuggerContextImpl context = getContext(project);
    return context != null ? context.getDebuggerSession() : null;
  }

  @Nullable
  private static DebuggerContextImpl getContext(@NotNull Project project) {
    return (DebuggerManagerEx.getInstanceEx(project)).getContext();
  }

  public boolean isEnabled(@NotNull final Project project, final AnActionEvent event) {
    final DebuggerContextImpl context = getContext(project);
    if (context == null) {
      return false;
    }

    DebuggerSession debuggerSession = context.getDebuggerSession();

    final boolean isPaused = debuggerSession != null && debuggerSession.isPaused();
    final SuspendContextImpl suspendContext = context.getSuspendContext();
    final boolean hasCurrentThread = suspendContext != null && suspendContext.getThread() != null;
    return isPaused && hasCurrentThread;
  }

}
