package com.intellij.find.editorHeaderActions;

import com.intellij.find.EditorSearchComponent;
import com.intellij.find.FindSettings;
import com.intellij.openapi.actionSystem.AnActionEvent;

public class ToggleWholeWordsOnlyAction extends EditorHeaderToggleAction {
  private static final String WHOLE_WORDS_ONLY = "Wo&rds";

  public ToggleWholeWordsOnlyAction(EditorSearchComponent editorSearchComponent) {
    super(editorSearchComponent, WHOLE_WORDS_ONLY);
  }

  @Override
  public boolean isSelected(AnActionEvent e) {
    return myEditorSearchComponent.getFindModel().isWholeWordsOnly();
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    e.getPresentation().setEnabled(!myEditorSearchComponent.getFindModel().isRegularExpressions());
    e.getPresentation().setVisible(!myEditorSearchComponent.getFindModel().isMultiline());
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    FindSettings.getInstance().setLocalWholeWordsOnly(state);
    myEditorSearchComponent.getFindModel().setWholeWordsOnly(state);
  }
}
