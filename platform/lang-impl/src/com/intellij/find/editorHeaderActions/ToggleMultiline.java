package com.intellij.find.editorHeaderActions;

import com.intellij.find.EditorSearchComponent;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;


public class ToggleMultiline extends ToggleAction {
  private final EditorSearchComponent myEditorSearchComponent;

  public ToggleMultiline(EditorSearchComponent editorSearchComponent) {
    super("Multiline", "Toggle Multiline Mode", AllIcons.Actions.ShowViewer);
    myEditorSearchComponent = editorSearchComponent;
  }

  @Override
  public boolean isSelected(AnActionEvent e) {
    return myEditorSearchComponent.getFindModel().isMultiline();
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    myEditorSearchComponent.getFindModel().setMultiline(state);
  }
}
