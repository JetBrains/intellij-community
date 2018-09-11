// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.actions;

import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.MethodFilter;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.xdebugger.impl.actions.DebuggerActionHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JvmSmartStepIntoActionHandler extends DebuggerActionHandler {
  @Override
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
    doStepInto(session, Registry.is("debugger.single.smart.step.force"), null);
  }

  static void doStepInto(DebuggerSession session, boolean force, MethodFilter filter) {
    session.sessionResumed();
    session.stepInto(force, filter);
  }

  @Override
  public boolean isEnabled(@NotNull final Project project, final AnActionEvent event) {
    final DebuggerContextImpl context = (DebuggerManagerEx.getInstanceEx(project)).getContext();
    DebuggerSession debuggerSession = context.getDebuggerSession();
    final boolean isPaused = debuggerSession != null && debuggerSession.isPaused();
    final SuspendContextImpl suspendContext = context.getSuspendContext();
    final boolean hasCurrentThread = suspendContext != null && suspendContext.getThread() != null;
    return isPaused && hasCurrentThread;
  }
}
