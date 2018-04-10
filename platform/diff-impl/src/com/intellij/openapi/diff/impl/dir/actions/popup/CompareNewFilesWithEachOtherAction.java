// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl.dir.actions.popup;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diff.impl.dir.DirDiffElementImpl;
import com.intellij.openapi.diff.impl.dir.DirDiffTableModel;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.Couple;
import com.intellij.util.containers.ContainerUtil;

import java.util.List;

/**
 * @author nik
 */
public class CompareNewFilesWithEachOtherAction extends DumbAwareAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    DirDiffTableModel model = SetOperationToBase.getModel(e);
    if (model == null) return;

    Couple<DirDiffElementImpl> couple = getSelectedSourceAndTarget(model);
    if (couple == null) return;

    model.setReplacement(couple.first, couple.second);
    model.reloadModel(false);
  }

  @Override
  public void update(AnActionEvent e) {
    DirDiffTableModel model = SetOperationToBase.getModel(e);
    if (model != null) {
      Couple<DirDiffElementImpl> target = getSelectedSourceAndTarget(model);
      if (target != null) {
        e.getPresentation().setEnabled(true);
        e.getPresentation().setDescription("Compare '" + target.first.getSourceName() + "' with '" + target.second.getTargetName() + "'");
        return;
      }
    }
    e.getPresentation().setDescription("Compare selected new files on the left side and on the right side with each other.");
    e.getPresentation().setEnabled(false);
  }

  private static Couple<DirDiffElementImpl> getSelectedSourceAndTarget(DirDiffTableModel model) {
    List<DirDiffElementImpl> elements = model.getSelectedElements();
    if (elements.size() != 2) return null;
    DirDiffElementImpl source = ContainerUtil.find(elements, DirDiffElementImpl::isSource);
    DirDiffElementImpl target = ContainerUtil.find(elements, DirDiffElementImpl::isTarget);
    if (source == null || target == null || source.getParentNode() != target.getParentNode()) return null;
    return Couple.of(source, target);
  }
}
