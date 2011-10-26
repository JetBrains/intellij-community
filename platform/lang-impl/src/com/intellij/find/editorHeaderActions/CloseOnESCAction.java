package com.intellij.find.editorHeaderActions;

import com.intellij.find.EditorSearchComponent;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.DumbAware;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;

public class CloseOnESCAction extends EditorHeaderAction  implements DumbAware {
  public CloseOnESCAction(EditorSearchComponent editorSearchComponent, JComponent textField) {
    super(editorSearchComponent);

    ArrayList<Shortcut> shortcuts = new ArrayList<Shortcut>();
    if (KeymapUtil.isEmacsKeymap()) {
      shortcuts.add(new KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_G, InputEvent.CTRL_MASK), null));
      textField.registerKeyboardAction(new ActionListener() {
                                         @Override
                                         public void actionPerformed(final ActionEvent e) {
                                           CloseOnESCAction.this.actionPerformed(null);
                                         }
                                       }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_FOCUSED);
    } else {
      shortcuts.add(new KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), null));
    }

    registerCustomShortcutSet(new CustomShortcutSet(shortcuts.toArray(new Shortcut[shortcuts.size()])), textField);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    getEditorSearchComponent().close();
  }
}
