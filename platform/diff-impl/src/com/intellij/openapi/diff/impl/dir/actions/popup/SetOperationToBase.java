/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.diff.impl.dir.actions.popup;

import com.intellij.ide.diff.DirDiffElement;
import com.intellij.ide.diff.DirDiffOperation;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diff.impl.dir.DirDiffElementImpl;
import com.intellij.openapi.diff.impl.dir.DirDiffPanel;
import com.intellij.openapi.diff.impl.dir.DirDiffTableModel;
import com.intellij.openapi.project.DumbAwareAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Konstantin Bulenkov
 */
public abstract class SetOperationToBase extends DumbAwareAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    final DirDiffTableModel model = getModel(e);
    final JTable table = getTable(e);
    assert model != null && table != null;
    for (DirDiffElementImpl element : model.getSelectedElements()) {
      if (isEnabledFor(element)) {
        element.setOperation(getOperation(element));
      } else {
        element.setOperation(DirDiffOperation.NONE);
      }
    }
    table.repaint();
  }

  @NotNull
  protected abstract DirDiffOperation getOperation(DirDiffElementImpl element);

  @Override
  public final void update(AnActionEvent e) {
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

  protected abstract boolean isEnabledFor(DirDiffElement element);

  @Nullable
  static JTable getTable(AnActionEvent e) {
    return e.getData(DirDiffPanel.DIR_DIFF_TABLE);
  }

  @Nullable
  public static DirDiffTableModel getModel(AnActionEvent e) {
    return e.getData(DirDiffPanel.DIR_DIFF_MODEL);
  }
}
