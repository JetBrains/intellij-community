// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl.dir.actions.popup;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.diff.impl.dir.DirDiffTableModel;

/**
 * @author nik
 */
public class DirDiffOperationsActionGroup extends DefaultActionGroup {
  @Override
  public void update(AnActionEvent e) {
    DirDiffTableModel model = SetOperationToBase.getModel(e);
    e.getPresentation().setEnabledAndVisible(model != null && model.isOperationsEnabled());
  }
}
