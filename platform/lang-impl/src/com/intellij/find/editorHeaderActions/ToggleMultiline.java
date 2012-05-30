package com.intellij.find.editorHeaderActions;

import com.intellij.find.EditorSearchComponent;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;

import javax.swing.*;


public class ToggleMultiline extends ToggleAction {
  private EditorSearchComponent myEditorSearchComponent;
  private static final Icon ICON = AllIcons.Actions.ShowViewer;

  public ToggleMultiline(EditorSearchComponent editorSearchComponent) {
    super("Multiline", "Toggle Multiline Mode", ICON);
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
