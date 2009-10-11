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
package com.intellij.refactoring.inline;

import com.intellij.openapi.project.Project;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.RefactoringMessageDialog;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;

/**
 * @author yole
 */
public class InlineParameterDialog extends RefactoringMessageDialog {
  private JCheckBox myCreateLocalCheckbox;

  public InlineParameterDialog(String title, String message, String helpTopic, @NonNls String iconId, boolean showCancelButton, Project project) {
    super(title, message, helpTopic, iconId, showCancelButton, project);
  }

  protected JComponent createNorthPanel() {
    JComponent superPanel = super.createNorthPanel();
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(superPanel, BorderLayout.CENTER);
    myCreateLocalCheckbox = new JCheckBox(RefactoringBundle.message("inline.parameter.replace.with.local.checkbox"));
    panel.add(myCreateLocalCheckbox, BorderLayout.SOUTH);
    return panel;
  }

  public boolean isCreateLocal() {
    return myCreateLocalCheckbox.isSelected();
  }

  public boolean showDialog() {
      show();
      return isOK();
  }

}
