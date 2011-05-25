package com.intellij.find.editorHeaderActions;

import com.intellij.find.EditorSearchComponent;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.util.IconLoader;

import javax.swing.*;


public class ToggleMultiline extends ToggleAction {
  private EditorSearchComponent myEditorSearchComponent;
  private static final Icon ICON = IconLoader.getIcon("/actions/showViewer.png");

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
