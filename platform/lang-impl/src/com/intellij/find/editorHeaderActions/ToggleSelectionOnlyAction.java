package com.intellij.find.editorHeaderActions;

import com.intellij.find.EditorSearchComponent;
import com.intellij.openapi.actionSystem.AnActionEvent;

public class ToggleSelectionOnlyAction extends EditorHeaderToggleAction {
  public ToggleSelectionOnlyAction(EditorSearchComponent editorSearchComponent) {
    super(editorSearchComponent, "Selection Only");
  }

  @Override
  public boolean isSelected(AnActionEvent e) {
    return !getEditorSearchComponent().getFindModel().isGlobal();
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    e.getPresentation().setVisible(getEditorSearchComponent().getFindModel().isReplaceState());
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    getEditorSearchComponent().getFindModel().setGlobal(!state);
  }
}
