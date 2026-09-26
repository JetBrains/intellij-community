// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diff.impl.dir.actions.popup;

import com.intellij.ide.diff.DirDiffOperation;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diff.impl.dir.DirDiffElementImpl;
import com.intellij.openapi.diff.impl.dir.DirDiffTableModel;
import com.intellij.openapi.project.DumbAwareAction;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.JTable;

/**
 * @author lene
 */
@ApiStatus.Internal
public class SetNoOperation extends DumbAwareAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final DirDiffTableModel model = SetOperationToBase.getModel(e);
    final JTable table = SetOperationToBase.getTable(e);
    assert model != null && table != null;
    for (DirDiffElementImpl element : model.getSelectedElements()) {
      element.setOperation(DirDiffOperation.NONE);
    }
    table.repaint();
  }

  @Override
  public final void update(@NotNull AnActionEvent e) {
    final DirDiffTableModel model = SetOperationToBase.getModel(e);
    final JTable table = SetOperationToBase.getTable(e);
    if (table != null && model != null && model.isOperationsEnabled()) {
      for (DirDiffElementImpl element : model.getSelectedElements()) {
        if (element.getOperation() != DirDiffOperation.NONE) {
          e.getPresentation().setEnabled(true);
          return;
        }
      }
    }
    e.getPresentation().setEnabled(false);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }
}
