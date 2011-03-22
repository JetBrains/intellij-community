package com.intellij.find.editorHeaderActions;

import com.intellij.find.EditorSearchComponent;
import com.intellij.find.FindSettings;
import com.intellij.openapi.actionSystem.AnActionEvent;

public class ToggleWholeWordsOnlyAction extends EditorHeaderToggleAction {
  public ToggleWholeWordsOnlyAction(EditorSearchComponent editorSearchComponent) {
    super(editorSearchComponent, "Whole words only");
  }

  @Override
  public boolean isSelected(AnActionEvent e) {
    return getEditorSearchComponent().getFindModel().isWholeWordsOnly();
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    e.getPresentation().setEnabled(!getEditorSearchComponent().getFindModel().isRegularExpressions());
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    FindSettings.getInstance().setLocalWholeWordsOnly(state);
    getEditorSearchComponent().getFindModel().setWholeWordsOnly(state);
  }
}
