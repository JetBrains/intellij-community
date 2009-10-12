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

import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.impl.actions.DebuggerToggleActionHandler;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 15, 2005
 */
public class MuteBreakpointsActionHandler extends DebuggerToggleActionHandler {
  public boolean isSelected(@NotNull final Project project, final AnActionEvent event) {
    DebuggerContextImpl context = DebuggerAction.getDebuggerContext(event.getDataContext());
    DebugProcessImpl debugProcess = context.getDebugProcess();
    return debugProcess != null && debugProcess.areBreakpointsMuted();
  }

  public void setSelected(@NotNull final Project project, final AnActionEvent event, final boolean state) {
    DebuggerContextImpl context = DebuggerAction.getDebuggerContext(event.getDataContext());
    final DebugProcessImpl debugProcess = context.getDebugProcess();
    if(debugProcess != null) {
      debugProcess.setBreakpointsMuted(state);
    }
  }

  public boolean isEnabled(@NotNull final Project project, final AnActionEvent event) {
    DebuggerContextImpl context = DebuggerAction.getDebuggerContext(event.getDataContext());
    final DebugProcessImpl debugProcess = context.getDebugProcess();
    return debugProcess != null; 
  }

}
