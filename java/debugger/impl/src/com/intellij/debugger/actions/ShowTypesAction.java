// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.actions;

import com.intellij.debugger.engine.JavaDebugProcess;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.RegistryToggleAction;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeState;
import org.jetbrains.annotations.NotNull;

public class ShowTypesAction extends RegistryToggleAction {
  public ShowTypesAction() {
    super("debugger.showTypes");
  }

  @Override
  public void doWhenDone(AnActionEvent e) {
    Project project = e.getProject();
    if (project != null) {
      if (DebuggerUIUtil.isInDetachedTree(e)) {
        XDebuggerTree tree = XDebuggerTree.getTree(e);
        if (tree != null) {
          tree.rebuildAndRestore(XDebuggerTreeState.saveState(tree));
        }
      }
      XDebugSession session = XDebuggerManager.getInstance(project).getCurrentSession();
      if (session != null) {
        session.rebuildViews();
      }
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project != null) {
      XDebugSession session = XDebuggerManager.getInstance(project).getCurrentSession();
      if (session != null && session.getDebugProcess() instanceof JavaDebugProcess) {
        e.getPresentation().setEnabledAndVisible(true);
        super.update(e);
        return;
      }
    }
    e.getPresentation().setEnabledAndVisible(false);
  }
}
