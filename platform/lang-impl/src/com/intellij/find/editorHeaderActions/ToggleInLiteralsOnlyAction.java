package com.intellij.find.editorHeaderActions;

import com.intellij.find.EditorSearchComponent;
import com.intellij.openapi.actionSystem.AnActionEvent;

public class ToggleInLiteralsOnlyAction extends EditorHeaderToggleAction {
  private static final String TEXT = "In Literals Only";

  public ToggleInLiteralsOnlyAction(EditorSearchComponent editorSearchComponent) {
    super(editorSearchComponent, TEXT);
  }

  @Override
  public boolean isSelected(AnActionEvent e) {
    return getEditorSearchComponent().getFindModel().isInStringLiteralsOnly();
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    getEditorSearchComponent().getFindModel().setInStringLiteralsOnly(state);
  }
}
