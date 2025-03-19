// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.action;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Vladislav.Soroka
 */
@ApiStatus.Internal
public abstract class ExternalSystemTreeAction extends ExternalSystemAction {

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  protected boolean isEnabled(@NotNull AnActionEvent e) {
    return super.isEnabled(e) && getTree(e) != null;
  }

  protected static @Nullable JTree getTree(@NotNull AnActionEvent e) {
    return e.getData(ExternalSystemDataKeys.PROJECTS_TREE);
  }

  public static class CollapseAll extends ExternalSystemTreeAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      JTree tree = getTree(e);
      if (tree == null) return;

      TreeUtil.collapseAll(tree, -1);
    }
  }

  public static class ExpandAll extends ExternalSystemTreeAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      JTree tree = getTree(e);
      if (tree == null) return;

      TreeUtil.expandAll(tree);
    }
  }
}

