// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.configuration;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.ui.TextAccessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public abstract class BrowseModuleValueActionListener<T extends JComponent> implements ActionListener {
  private final Project myProject;
  private TextAccessor myField;

  protected BrowseModuleValueActionListener(Project project) {
    myProject = project;
  }

  public Project getProject() {
    return myProject;
  }

  public JComponent getField() {
    return (JComponent)myField;
  }

  public void setField(@NotNull ComponentWithBrowseButton<T> field) {
    setTextAccessor((TextAccessor)field);
    field.addActionListener(this);
    field.setButtonEnabled(!myProject.isDefault());
  }

  public void setTextAccessor(@NotNull TextAccessor accessor) {
    myField = accessor;
  }

  public String getText() {
    return myField.getText();
  }

  public void detach() {
    if (myField instanceof ComponentWithBrowseButton) {
      ((ComponentWithBrowseButton<?>)myField).removeActionListener(this);
      myField = null;
    }
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    String text = showDialog();
    if (text != null) {
      myField.setText(text);
    }
  }

  protected abstract @Nullable String showDialog();
}