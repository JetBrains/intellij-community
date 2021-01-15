// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.find.editorHeaderActions;

import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.project.DumbAwareAction;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class ShowMoreOptions extends DumbAwareAction implements LightEditCompatible {

  private final ActionToolbarImpl myToolbarComponent;

  //placeholder for keymap
  public ShowMoreOptions() {
    myToolbarComponent = null;
  }

  public ShowMoreOptions(ActionToolbarImpl toolbarComponent, JComponent shortcutHolder) {
    this.myToolbarComponent = toolbarComponent;
    KeyboardShortcut keyboardShortcut = ActionManager.getInstance().getKeyboardShortcut("ShowFilterPopup");
    if (keyboardShortcut != null) {
      registerCustomShortcutSet(new CustomShortcutSet(keyboardShortcut), shortcutHolder);
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final ActionButton secondaryActions = myToolbarComponent.getSecondaryActionsButton();
    if (secondaryActions != null) {
      secondaryActions.click();
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(myToolbarComponent != null && myToolbarComponent.getSecondaryActionsButton() != null);
  }
}
