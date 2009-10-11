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

/**
 * class ExportThreadsAction
 * @author Jeka
 */
package com.intellij.debugger.actions;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.util.IconLoader;

public class KillProcessAction extends AnAction {
  public KillProcessAction() {
    super(DebuggerBundle.message("action.kill.process.text"), DebuggerBundle.message("action.kill.process.description"), IconLoader.getIcon("/debugger/killProcess.png"));
  }

  public void actionPerformed(AnActionEvent e) {
    final DebuggerContextImpl context = DebuggerAction.getDebuggerContext(e.getDataContext());
    if (DebuggerContextImpl.EMPTY_CONTEXT.equals(context)) {
      return;
    }

    final DebuggerSession session = context.getDebuggerSession();
    if (session != null) {
      session.getProcess().stop(true);
    }
  }

  public void update(AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    final DebuggerContextImpl context = DebuggerAction.getDebuggerContext(event.getDataContext());
    if (DebuggerContextImpl.EMPTY_CONTEXT.equals(context)) {
      presentation.setEnabled(false);
      return;
    }
    DebuggerSession debuggerSession = context.getDebuggerSession();
    presentation.setEnabled(debuggerSession != null && debuggerSession.isAttached());
  }
}