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

  public void detach() {
    if (myField != null) {
      myField.removeActionListener(this);
      myField = null;
    }
  }

}
