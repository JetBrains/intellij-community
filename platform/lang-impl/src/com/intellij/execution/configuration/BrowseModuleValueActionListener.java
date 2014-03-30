/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.execution.configuration;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.TextAccessor;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public abstract class BrowseModuleValueActionListener<T extends JComponent> implements ActionListener {
  private ComponentWithBrowseButton<T> myField;
  private final Project myProject;

  protected BrowseModuleValueActionListener(final Project project) {
    myProject = project;
  }

  public void setField(final ComponentWithBrowseButton<T> field) {
    myField = field;
    myField.addActionListener(this);
    myField.setButtonEnabled(!myProject.isDefault());
  }

  @Override
  public void actionPerformed(final ActionEvent e) {
    final String text = showDialog();
    if (text != null) ((TextAccessor)myField).setText(text);
  }

  public String getText() {
    return ((TextAccessor)myField).getText();
  }

  public JComponent getField() { return myField; }

  @Nullable
  protected abstract String showDialog();

  public Project getProject() { return myProject; }

  public void detach() {
    if (myField != null) {
      myField.removeActionListener(this);
      myField = null;
    }
  }

}
