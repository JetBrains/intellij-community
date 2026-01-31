// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diff.impl.dir.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.diff.impl.dir.DirDiffTableModel;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import static com.intellij.ide.diff.DirDiffOperation.COPY_FROM;
import static com.intellij.ide.diff.DirDiffOperation.COPY_TO;
import static com.intellij.ide.diff.DirDiffOperation.DELETE;

/**
 * @author Konstantin Bulenkov
 */
@ApiStatus.Internal
public class SynchronizeDiff extends DirDiffAction {
  private final boolean mySelectedOnly;

  public SynchronizeDiff(DirDiffTableModel model, boolean selectedOnly) {
    super(model);
    mySelectedOnly = selectedOnly;
    ActionUtil.copyFrom(this, selectedOnly ? "DirDiffMenu.SynchronizeDiff" : "DirDiffMenu.SynchronizeDiff.All");
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    if (!e.getPresentation().isEnabled()) {
      return;
    }
    boolean enabled = e.getData(CommonDataKeys.EDITOR) == null || e.isFromActionToolbar();
    enabled &= !JBIterable.from(mySelectedOnly ? getModel().getSelectedElements() : getModel().getElements())
      .filter(d -> d.getOperation() == COPY_FROM || d.getOperation() == COPY_TO || d.getOperation() == DELETE)
      .filter(d -> d.getSource() == null || d.getSource().isOperationsEnabled())
      .filter(d -> d.getTarget() == null || d.getTarget().isOperationsEnabled())
      .isEmpty();
    e.getPresentation().setEnabled(enabled);
  }

  @Override
  protected void updateState(boolean state) {
    if (mySelectedOnly) {
      getModel().synchronizeSelected();
    }
    else {
      getModel().synchronizeAll();
    }
  }

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    return false;
  }

  @Override
  protected boolean isReloadNeeded() {
    return false;
  }
}
