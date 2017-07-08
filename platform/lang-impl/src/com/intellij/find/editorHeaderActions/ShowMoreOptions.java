package com.intellij.find.editorHeaderActions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.project.DumbAware;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

public class ShowMoreOptions extends AnAction implements DumbAware {
  public static final Shortcut SHORT_CUT = new KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.ALT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK), null);

  private final ActionToolbarImpl myToolbarComponent;

  public ShowMoreOptions(ActionToolbarImpl toolbarComponent, JComponent shortcutHolder) {
    this.myToolbarComponent = toolbarComponent;
    registerCustomShortcutSet(new CustomShortcutSet(SHORT_CUT), shortcutHolder);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final ActionButton secondaryActions = myToolbarComponent.getSecondaryActionsButton();
    if (secondaryActions != null) {
      secondaryActions.click();
    }
  }
}
