// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl.dir.actions.popup;

import com.intellij.ide.diff.DiffType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diff.impl.dir.DirDiffElementImpl;
import com.intellij.openapi.diff.impl.dir.DirDiffTableModel;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.Comparing;

/**
 * @author nik
 */
public class CancelComparingNewFilesWithEachOtherAction extends DumbAwareAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    DirDiffTableModel model = SetOperationToBase.getModel(e);
    if (model == null) return;
    for (DirDiffElementImpl element : model.getSelectedElements()) {
      if (isChangedOrEqual(element)) {
        model.setReplacement(element, null);
      }
    }
    model.reloadModel(false);
  }

  @Override
  public void update(AnActionEvent e) {
    DirDiffTableModel model = SetOperationToBase.getModel(e);
    e.getPresentation().setEnabledAndVisible(model != null && model.getSelectedElements().stream().anyMatch(
      it -> isChangedOrEqual(it) && Comparing.equal(model.getReplacementName(it), it.getTargetName())
    ));
  }

  private static boolean isChangedOrEqual(DirDiffElementImpl element) {
    return element.getType() == DiffType.CHANGED || element.getType() == DiffType.EQUAL;
  }
}
