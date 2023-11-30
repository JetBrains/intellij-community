// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.tree;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.project.DumbAware;
import com.intellij.ui.treeStructure.treetable.TreeTable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

abstract class BaseTreeNodeAction extends AnAction implements ActionRemoteBehaviorSpecification.Frontend, DumbAware {
  BaseTreeNodeAction() {
    setEnabledInModalContext(true);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Object sourceComponent = getSourceComponent(e);
    if (sourceComponent instanceof JTree) {
      performOn((JTree)sourceComponent);
    }
    else if (sourceComponent instanceof TreeTable) {
      performOn(((TreeTable)sourceComponent).getTree());
    }
  }

  protected abstract void performOn(JTree tree);

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(enabledOn(getSourceComponent(e)));
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  private static boolean enabledOn(Object sourceComponent) {
    if (sourceComponent instanceof JTree) {
      return true;
    }
    if (sourceComponent instanceof TreeTable) {
      return true;
    }
    return false;
  }

  private static Object getSourceComponent(@NotNull AnActionEvent e) {
    return e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT);
  }
}