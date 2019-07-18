// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl.dir.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.actionSystem.ShortcutSet;
import com.intellij.openapi.diff.impl.dir.DirDiffTableModel;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Bulenkov
 */
public class RefreshDirDiffAction extends DirDiffAction {
  public static final CustomShortcutSet REFRESH_SHORTCUT = CustomShortcutSet.fromString(SystemInfo.isMac ? "meta R" : "F5");

  public RefreshDirDiffAction(DirDiffTableModel model) {
    super(model);
    getTemplatePresentation().setText("Refresh");
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
    return REFRESH_SHORTCUT;
  }

  @Override
  protected boolean isFullReload() {
    return true;
  }
}
