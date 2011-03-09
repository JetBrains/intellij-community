package com.intellij.find.editorHeaderActions;

import com.intellij.find.EditorSearchComponent;
import com.intellij.openapi.actionSystem.ToggleAction;

public abstract class EditorHeaderToggleAction extends ToggleAction {

  public EditorSearchComponent getEditorSearchComponent() {
    return myEditorSearchComponent;
  }

  private EditorSearchComponent myEditorSearchComponent;

  protected EditorHeaderToggleAction(EditorSearchComponent editorSearchComponent, String text) {
    super(text);
    myEditorSearchComponent = editorSearchComponent;
  }
}
