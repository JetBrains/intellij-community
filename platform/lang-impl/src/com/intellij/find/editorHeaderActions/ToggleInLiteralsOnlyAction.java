package com.intellij.find.editorHeaderActions;

import com.intellij.find.EditorSearchComponent;
import com.intellij.find.FindModel;
import com.intellij.openapi.actionSystem.AnActionEvent;

public class ToggleInLiteralsOnlyAction extends EditorHeaderToggleAction  implements SecondaryHeaderAction {
  private static final String TEXT = "In &Literals Only";

  public ToggleInLiteralsOnlyAction(EditorSearchComponent editorSearchComponent) {
    super(editorSearchComponent, TEXT);
  }

  @Override
  public boolean isSelected(AnActionEvent e) {
    return getEditorSearchComponent().getFindModel().isInStringLiteralsOnly();
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    getEditorSearchComponent().getFindModel().setSearchContext(state ? FindModel.SearchContext.IN_STRING_LITERALS : FindModel.SearchContext.ANY);
  }
}
