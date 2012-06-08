package com.intellij.find.editorHeaderActions;

import com.intellij.find.EditorSearchComponent;
import com.intellij.openapi.actionSystem.AnActionEvent;

public class ToggleSelectionOnlyAction extends EditorHeaderToggleAction {
  private static final String SELECTION_ONLY = "In &Selection";

  public ToggleSelectionOnlyAction(EditorSearchComponent editorSearchComponent) {
    super(editorSearchComponent, SELECTION_ONLY);
  }

  @Override
  public boolean isSelected(AnActionEvent e) {
    return !getEditorSearchComponent().getFindModel().isGlobal();
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    final boolean replaceState = getEditorSearchComponent().getFindModel().isReplaceState();
    e.getPresentation().setVisible(replaceState);
    e.getPresentation().setEnabled(replaceState);
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    getEditorSearchComponent().getFindModel().setGlobal(!state);
  }
}
