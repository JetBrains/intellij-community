package com.intellij.find.editorHeaderActions;

import com.intellij.find.EditorSearchComponent;
import com.intellij.find.FindModel;
import com.intellij.openapi.actionSystem.AnActionEvent;

public class TogglePreserveCaseAction extends EditorHeaderToggleAction {
  private static final String TEXT = "Preserve Case";

  public TogglePreserveCaseAction(EditorSearchComponent editorSearchComponent) {
    super(editorSearchComponent, TEXT, "r");
  }

  @Override
  public boolean isSelected(AnActionEvent e) {
    return getEditorSearchComponent().getFindModel().isPreserveCase();
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    FindModel findModel = getEditorSearchComponent().getFindModel();
    e.getPresentation().setVisible(findModel.isReplaceState());
    e.getPresentation().setEnabled(!findModel.isRegularExpressions());
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    getEditorSearchComponent().getFindModel().setPreserveCase(state);
  }
}
