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
package com.intellij.debugger.actions;

import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.xdebugger.impl.actions.DebuggerActionHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JvmSmartStepIntoActionHandler extends DebuggerActionHandler {
  public void perform(@NotNull final Project project, final AnActionEvent event) {
    final DebuggerContextImpl debuggerContext = (DebuggerManagerEx.getInstanceEx(project)).getContext();
    final DebuggerSession session = debuggerContext.getDebuggerSession();
    if (session != null) {
      doStep(project, debuggerContext.getSourcePosition(), session);
    }
  }

  private static void doStep(final @NotNull Project project, final @Nullable SourcePosition position, final @NotNull DebuggerSession session) {
    final VirtualFile file = position != null ? position.getFile().getVirtualFile() : null;
    final FileEditor fileEditor = file != null? FileEditorManager.getInstance(project).getSelectedEditor(file) : null;
    if (fileEditor instanceof TextEditor) {
      for (JvmSmartStepIntoHandler handler : Extensions.getExtensions(JvmSmartStepIntoHandler.EP_NAME)) {
        if (handler.isAvailable(position) && handler.doSmartStep(position, session, (TextEditor)fileEditor)) {
          return;
        }
      }
    }
    session.sessionResumed();
    session.stepInto(true, null);
  }

  public boolean isEnabled(@NotNull final Project project, final AnActionEvent event) {
    final DebuggerContextImpl context = (DebuggerManagerEx.getInstanceEx(project)).getContext();
    DebuggerSession debuggerSession = context.getDebuggerSession();
    final boolean isPaused = debuggerSession != null && debuggerSession.isPaused();
    final SuspendContextImpl suspendContext = context.getSuspendContext();
    final boolean hasCurrentThread = suspendContext != null && suspendContext.getThread() != null;
    return isPaused && hasCurrentThread;
  }
}
