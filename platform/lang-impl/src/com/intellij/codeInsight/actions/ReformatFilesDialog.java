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

package com.intellij.codeInsight.actions;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;

import javax.swing.*;

public class ReformatFilesDialog extends DialogWrapper {
  private JPanel myPanel;
  private JCheckBox myOptimizeImports;

  public ReformatFilesDialog(Project project) {
    super(project, true);
    setTitle(CodeInsightBundle.message("dialog.reformat.files.title"));
    myOptimizeImports.setSelected(isOptmizeImportsOptionOn());
    init();
  }

  protected JComponent createCenterPanel() {
    return myPanel;
  }

  public boolean optimizeImports(){
    return myOptimizeImports.isSelected();
  }

  protected void doOKAction() {
    PropertiesComponent.getInstance().setValue(LayoutCodeDialog.OPTIMIZE_IMPORTS_KEY, Boolean.toString(myOptimizeImports.isSelected()));
    super.doOKAction();
  }

  static boolean isOptmizeImportsOptionOn() {
    return Boolean.valueOf(PropertiesComponent.getInstance().getValue(LayoutCodeDialog.OPTIMIZE_IMPORTS_KEY));
  }

}
