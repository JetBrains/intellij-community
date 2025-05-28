// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diff.impl.dir.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.actionSystem.ShortcutSet;
import com.intellij.openapi.client.ClientSystemInfo;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.diff.impl.dir.DirDiffTableModel;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Bulenkov
 */
@ApiStatus.Internal
public class RefreshDirDiffAction extends DirDiffAction {

  public RefreshDirDiffAction(DirDiffTableModel model) {
    super(model);
    getTemplatePresentation().setText(DiffBundle.messagePointer("action.presentation.RefreshDirDiffAction.text"));
    getTemplatePresentation().setIcon(PlatformIcons.SYNCHRONIZE_ICON);
  }

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    return false;
  }

  @Override
  protected void updateState(boolean state) {
    getModel().updateFromUI();
  }

  @Override
  public ShortcutSet getShortcut() {
    return CustomShortcutSet.fromString(ClientSystemInfo.isMac() ? "meta R" : "F5");
  }

  @Override
  protected boolean isFullReload() {
    return true;
  }
}
