package com.intellij.find.editorHeaderActions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.project.DumbAware;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

/**
* Created by IntelliJ IDEA.
* User: zajac
* Date: 25.05.11
* Time: 22:24
* To change this template use File | Settings | File Templates.
*/
public class ShowMoreOptions extends AnAction implements DumbAware {
  public static final Shortcut SHORT_CUT = new KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, InputEvent.ALT_DOWN_MASK), null);

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
