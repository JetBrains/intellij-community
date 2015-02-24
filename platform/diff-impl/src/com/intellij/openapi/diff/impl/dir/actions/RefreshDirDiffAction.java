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
package com.intellij.openapi.diff.impl.dir.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.actionSystem.ShortcutSet;
import com.intellij.openapi.diff.impl.dir.DirDiffTableModel;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.PlatformIcons;

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
  public boolean isSelected(AnActionEvent e) {
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
