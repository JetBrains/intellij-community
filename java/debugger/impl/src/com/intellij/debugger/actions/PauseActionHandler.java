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
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.impl.actions.DebuggerActionHandler;
import com.sun.jdi.request.EventRequest;
import org.jetbrains.annotations.NotNull;

public class PauseActionHandler extends DebuggerActionHandler {
  public void perform(@NotNull final Project project, final AnActionEvent event) {
    final DebuggerSession session = (DebuggerManagerEx.getInstanceEx(project)).getContext().getDebuggerSession();
    if (session != null) {
      session.pause();
    }
  }

  @Override
  public boolean isHidden(@NotNull Project project, AnActionEvent event) {
    return DebuggerManagerEx.getInstanceEx(project).getContext().getDebuggerSession() == null;
  }

  public boolean isEnabled(@NotNull final Project project, final AnActionEvent event) {
    final DebuggerSession debuggerSession = DebuggerManagerEx.getInstanceEx(project).getContext().getDebuggerSession();
    return debuggerSession != null && !debuggerSession.getProcess().isPausePressed() &&
           (debuggerSession.isEvaluating() || 
            debuggerSession.isRunning() || isSingleThreadSuspended(debuggerSession)
           );
  }

  private static boolean isSingleThreadSuspended(final DebuggerSession debuggerSession) {
    final SuspendContextImpl suspendContext = debuggerSession.getContextManager().getContext().getSuspendContext();
    return suspendContext != null && !suspendContext.isResumed() && suspendContext.getSuspendPolicy() == EventRequest.SUSPEND_EVENT_THREAD;
  }
}
