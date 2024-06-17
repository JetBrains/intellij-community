// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diff.impl.dir.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.diff.impl.dir.DirDiffTableModel;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Bulenkov
 */
@ApiStatus.Internal
public class EnableRight extends DirDiffAction {
  protected EnableRight(DirDiffTableModel model) {
    super(model);
    ActionUtil.copyFrom(this, "DirDiffMenu.EnableRight");
  }

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    return getModel().isShowNewOnTarget();
  }

  @Override
  public void updateState(boolean state) {
    getModel().setShowNewOnTarget(state);
  }
}
