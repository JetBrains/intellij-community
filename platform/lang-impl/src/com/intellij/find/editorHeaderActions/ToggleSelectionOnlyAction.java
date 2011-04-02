package com.intellij.find.editorHeaderActions;

import com.intellij.find.EditorSearchComponent;
import com.intellij.openapi.actionSystem.AnActionEvent;

public class ToggleSelectionOnlyAction extends EditorHeaderToggleAction {
  private static final String SELECTION_ONLY = "Selection Only";

  public ToggleSelectionOnlyAction(EditorSearchComponent editorSearchComponent) {
    super(editorSearchComponent, SELECTION_ONLY, "e");
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
