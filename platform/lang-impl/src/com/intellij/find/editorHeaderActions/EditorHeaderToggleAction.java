package com.intellij.find.editorHeaderActions;

import com.intellij.find.EditorSearchComponent;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.CheckboxAction;
import com.intellij.openapi.project.DumbAware;

import javax.swing.*;

public abstract class EditorHeaderToggleAction extends CheckboxAction implements DumbAware {

  @Override
  public boolean displayTextInToolbar() {
    return true;
  }

  public EditorSearchComponent getEditorSearchComponent() {
    return myEditorSearchComponent;
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);
  }

  @Override
  public JComponent createCustomComponent(Presentation presentation) {
    final JComponent customComponent = super.createCustomComponent(presentation);
    customComponent.setFocusable(false);
    customComponent.setOpaque(false);
    return customComponent;
  }

  private final EditorSearchComponent myEditorSearchComponent;

  protected EditorHeaderToggleAction(EditorSearchComponent editorSearchComponent, String text) {
    super(text);
    myEditorSearchComponent = editorSearchComponent;
  }
}
