package com.intellij.find.editorHeaderActions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

/**
* Created by IntelliJ IDEA.
* User: zajac
* Date: 25.05.11
* Time: 22:24
* To change this template use File | Settings | File Templates.
*/
public class ShowMoreOptions extends AnAction {
  private JComponent myToolbarComponent;
  public static final Shortcut SHORT_CUT = new KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_M, InputEvent.ALT_DOWN_MASK), null);

  public ShowMoreOptions(JComponent toolbarComponent, JTextComponent searchField) {
    this.myToolbarComponent = toolbarComponent;
    registerCustomShortcutSet(new CustomShortcutSet(SHORT_CUT), searchField);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final ActionButton secondaryActions = ((ActionToolbarImpl)myToolbarComponent).getSecondaryActionsButton();
    if (secondaryActions != null) {
      secondaryActions.click();
    }
  }
}
