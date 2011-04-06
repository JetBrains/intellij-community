package com.intellij.find.editorHeaderActions;

import com.intellij.find.EditorSearchComponent;
import com.intellij.openapi.actionSystem.*;
import com.intellij.util.ui.EmptyIcon;

import javax.swing.*;
import java.awt.event.InputEvent;

public abstract class EditorHeaderToggleAction extends ToggleAction {

  @Override
  public boolean displayTextInToolbar() {
    return true;
  }

  public EditorSearchComponent getEditorSearchComponent() {
    return myEditorSearchComponent;
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    if (e == null) return;
    if (e.getPresentation().getIcon() == null && !isSecondary()) {
      e.getPresentation().setIcon(new EmptyIcon(1, 1));
    }
  }

  private boolean isSecondary() {
    return this instanceof SecondaryHeaderAction;
  }

  private EditorSearchComponent myEditorSearchComponent;

  protected EditorHeaderToggleAction(EditorSearchComponent editorSearchComponent, String text) {
    super(text);
    myEditorSearchComponent = editorSearchComponent;
    if (!isSecondary()) {
      final int m = text.indexOf('&');
      if (m != -1 && m < text.length()-1) {
        char c = text.charAt(m+1);
        Shortcut shortcut = new KeyboardShortcut(KeyStroke.getKeyStroke(Character.toUpperCase(c), InputEvent.ALT_DOWN_MASK), null);
        registerCustomShortcutSet(new CustomShortcutSet(shortcut), myEditorSearchComponent);
      }
    }
  }
}
