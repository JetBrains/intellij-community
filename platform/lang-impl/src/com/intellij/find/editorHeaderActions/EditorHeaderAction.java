package com.intellij.find.editorHeaderActions;


import com.intellij.find.EditorSearchComponent;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Shortcut;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public abstract class EditorHeaderAction extends AnAction {
  private final EditorSearchComponent myEditorSearchComponent;

  protected static void registerShortcutsForComponent(List<Shortcut> shortcuts, JComponent component, AnAction a) {
    a.registerCustomShortcutSet(
      new CustomShortcutSet(shortcuts.toArray(new Shortcut[shortcuts.size()])),
      component);
  }

  public EditorSearchComponent getEditorSearchComponent() {
    return myEditorSearchComponent;
  }

  protected EditorHeaderAction(EditorSearchComponent editorSearchComponent) {

    myEditorSearchComponent = editorSearchComponent;
  }
}

