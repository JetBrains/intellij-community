package com.intellij.find.editorHeaderActions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class ShowMoreOptions extends AnAction implements DumbAware {

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
