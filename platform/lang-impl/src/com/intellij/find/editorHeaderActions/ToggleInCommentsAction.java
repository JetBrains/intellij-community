package com.intellij.find.editorHeaderActions;

import com.intellij.find.EditorSearchComponent;
import com.intellij.openapi.actionSystem.AnActionEvent;

public class ToggleInCommentsAction extends EditorHeaderToggleAction {
  private static final String TEXT = "In Comments Only";

  public ToggleInCommentsAction(EditorSearchComponent editorSearchComponent) {
    super(editorSearchComponent, TEXT, null);
  }

  @Override
  public boolean isSelected(AnActionEvent e) {
    return getEditorSearchComponent().getFindModel().isInCommentsOnly();
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    getEditorSearchComponent().getFindModel().setInCommentsOnly(state);
  }
}
