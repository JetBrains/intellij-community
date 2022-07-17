// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;


public class ScrollTreeToCenterAction extends AnAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final Component component = e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT);
    if (component instanceof JTree) {
      JTree tree = (JTree)component;
      final int[] selection = tree.getSelectionRows();
       if (selection != null && selection.length > 0) {
        TreeUtil.showRowCentered(tree, selection [0], false);
      }
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT) instanceof JTree);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }
}