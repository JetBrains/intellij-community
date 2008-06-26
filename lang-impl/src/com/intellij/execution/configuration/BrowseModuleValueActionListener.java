package com.intellij.execution.configuration;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import org.jetbrains.annotations.Nullable;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public abstract class BrowseModuleValueActionListener implements ActionListener {
  private TextFieldWithBrowseButton myField;
  private final Project myProject;

  protected BrowseModuleValueActionListener(final Project project) {
    myProject = project;
  }

  public void setField(final TextFieldWithBrowseButton field) {
    myField = field;
    myField.addActionListener(this);
    myField.setButtonEnabled(!myProject.isDefault());
  }

  public void actionPerformed(final ActionEvent e) {
    final String text = showDialog();
    if (text != null) myField.getTextField().setText(text);
  }

  public String getText() {
    return myField.getText();
  }

  public TextFieldWithBrowseButton getField() { return myField; }

  @Nullable
  protected abstract String showDialog();

  public Project getProject() { return myProject; }
}
