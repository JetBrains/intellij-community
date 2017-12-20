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

import com.intellij.ide.diff.DirDiffOperation;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diff.impl.dir.DirDiffElementImpl;
import com.intellij.openapi.diff.impl.dir.DirDiffTableModel;
import com.intellij.openapi.project.DumbAwareAction;

import javax.swing.*;

/**
 * @author lene
 */
public class SetNoOperation extends DumbAwareAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    final DirDiffTableModel model = SetOperationToBase.getModel(e);
    final JTable table = SetOperationToBase.getTable(e);
    assert model != null && table != null;
    for (DirDiffElementImpl element : model.getSelectedElements()) {
        element.setOperation(DirDiffOperation.NONE);
    }
    table.repaint();
  }

  @Override
  public final void update(AnActionEvent e) {
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
}
