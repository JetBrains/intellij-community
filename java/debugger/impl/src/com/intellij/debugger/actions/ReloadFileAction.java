// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.actions;

import com.intellij.compiler.actions.CompileAction;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.ui.HotSwapUI;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.xdebugger.impl.hotswap.HotSwapStatistics;
import com.intellij.xdebugger.impl.rpc.HotSwapSource;
import org.jetbrains.annotations.NotNull;

public class ReloadFileAction extends CompileAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project != null) {
      VirtualFile[] files = getCompilableFiles(project, e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY));
      if (files.length > 0) {
        DebuggerSession session = DebuggerManagerEx.getInstanceEx(project).getContext().getDebuggerSession();
        if (session != null) {
          HotSwapStatistics.logHotSwapCalled(project, HotSwapSource.RELOAD_FILE);
          HotSwapUI.getInstance(project).compileAndReload(session, files);
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
