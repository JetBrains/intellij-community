package com.intellij.find.editorHeaderActions;

import com.intellij.find.EditorSearchComponent;
import com.intellij.find.FindModel;
import com.intellij.openapi.actionSystem.AnActionEvent;

public class TogglePreserveCaseAction extends EditorHeaderToggleAction  implements SecondaryHeaderAction {
  private static final String TEXT = "Preser&ve Case";

  public TogglePreserveCaseAction(EditorSearchComponent editorSearchComponent) {
    super(editorSearchComponent, TEXT);
  }

  @Override
  public boolean isSelected(AnActionEvent e) {
    return getEditorSearchComponent().getFindModel().isPreserveCase();
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    FindModel findModel = getEditorSearchComponent().getFindModel();
    e.getPresentation().setVisible(findModel.isReplaceState() && !findModel.isMultiline());
    e.getPresentation().setEnabled(!findModel.isRegularExpressions());
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    getEditorSearchComponent().getFindModel().setPreserveCase(state);
  }
}
