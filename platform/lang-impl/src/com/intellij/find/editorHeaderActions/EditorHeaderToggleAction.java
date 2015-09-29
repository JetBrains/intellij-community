package com.intellij.find.editorHeaderActions;

import com.intellij.find.SearchSession;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.CheckboxAction;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public abstract class EditorHeaderToggleAction extends CheckboxAction implements DumbAware {
  protected EditorHeaderToggleAction(@NotNull String text) {
    super(text);
  }

  @Override
  public boolean displayTextInToolbar() {
    return true;
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

  @Override
  public boolean isSelected(AnActionEvent e) {
    SearchSession search = e.getData(SearchSession.KEY);
    return search != null && isSelected(search);
  }

  @Override
  public void setSelected(AnActionEvent e, boolean selected) {
    SearchSession search = e.getData(SearchSession.KEY);
    if (search != null) {
      setSelected(search, selected);
    }
  }

  protected abstract boolean isSelected(@NotNull SearchSession session);

  protected abstract void setSelected(@NotNull SearchSession session, boolean selected);
}
