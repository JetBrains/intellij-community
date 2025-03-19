// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.tree;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.ui.ComponentUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Konstantin Bulenkov
 */
final class ExpandAll extends DumbAwareAction {

  ExpandAll() {
    setEnabledInModalContext(true);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    JTree tree = ComponentUtil.getParentOfType((Class<? extends JTree>)JTree.class, e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT));
    if (tree != null) {
      TreeUtil.expandAll(tree);
    }
  }
}