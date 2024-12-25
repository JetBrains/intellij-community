// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diff.impl.dir.actions.popup;

import com.intellij.ide.diff.DirDiffElement;
import com.intellij.ide.diff.DirDiffOperation;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diff.impl.dir.DirDiffElementImpl;
import com.intellij.openapi.diff.impl.dir.DirDiffPanel;
import com.intellij.openapi.diff.impl.dir.DirDiffTableModel;
import com.intellij.openapi.project.DumbAwareAction;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Konstantin Bulenkov
 */
@ApiStatus.Internal
public abstract class SetOperationToBase extends DumbAwareAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final DirDiffTableModel model = getModel(e);
    final JTable table = getTable(e);
    assert model != null && table != null;
    for (DirDiffElementImpl element : model.getSelectedElements()) {
      if (isEnabledFor(element)) {
        element.setOperation(getOperation(element));
      }
      else {
        element.setOperation(DirDiffOperation.NONE);
      }
    }
    table.repaint();
  }

  protected abstract @NotNull DirDiffOperation getOperation(DirDiffElementImpl element);

  @Override
  public final void update(@NotNull AnActionEvent e) {
    final DirDiffTableModel model = getModel(e);
    final JTable table = getTable(e);
    if (table != null && model != null && model.isOperationsEnabled()) {
      for (DirDiffElementImpl element : model.getSelectedElements()) {
        if (isEnabledFor(element)) {
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

  protected abstract boolean isEnabledFor(DirDiffElement element);

  static @Nullable JTable getTable(AnActionEvent e) {
    return e.getData(DirDiffPanel.DIR_DIFF_TABLE);
  }

  public static @Nullable DirDiffTableModel getModel(AnActionEvent e) {
    return e.getData(DirDiffPanel.DIR_DIFF_MODEL);
  }
}
