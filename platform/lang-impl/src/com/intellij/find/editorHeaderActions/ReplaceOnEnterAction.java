package com.intellij.find.editorHeaderActions;

import com.intellij.find.EditorSearchComponent;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Shortcut;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.util.ArrayList;

public class ReplaceOnEnterAction extends EditorHeaderAction {
  public ReplaceOnEnterAction(EditorSearchComponent editorSearchComponent, JComponent textField) {
    super(editorSearchComponent);
    ArrayList<Shortcut> shortcuts = new ArrayList<Shortcut>();
    shortcuts.add(new KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), null));

    registerCustomShortcutSet(new CustomShortcutSet(shortcuts.toArray(new Shortcut[shortcuts.size()])), textField);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    getEditorSearchComponent().replaceCurrent();
  }
}
