// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.debugger.impl.compiler;

import com.intellij.compiler.actions.CompileAction;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.ui.HotSwapUI;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.xdebugger.hotswap.HotSwapSource;
import org.jetbrains.annotations.NotNull;

final class ReloadFileAction extends CompileAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project != null) {
      VirtualFile[] files = getCompilableFiles(project, e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY));
      if (files.length > 0) {
        DebuggerSession session = DebuggerManagerEx.getInstanceEx(project).getContext().getDebuggerSession();
        if (session != null) {
          HotSwapUI.getInstance(project).compileAndReload(session, HotSwapSource.RELOAD_FILE, files);
        }
      }
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    e.getPresentation().setEnabledAndVisible(
      project != null &&
      DebuggerManagerEx.getInstanceEx(project).getContext().getDebuggerSession() != null &&
      getCompilableFiles(project, e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)).length > 0 &&
      !CompilerManager.getInstance(project).isCompilationActive());
  }
}
