/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class ReformatFilesDialog extends DialogWrapper implements ReformatFilesOptions {
  @NotNull private Project myProject;
  private JPanel myPanel;
  private JCheckBox myOptimizeImports;
  private JCheckBox myRearrangeEntriesCb;

  public ReformatFilesDialog(@NotNull Project project, @NotNull VirtualFile[] files) {
    super(project, true);
    myProject = project;
    setTitle(CodeInsightBundle.message("dialog.reformat.files.title"));
    myOptimizeImports.setSelected(isOptmizeImportsOptionOn());
    myOptimizeImports.setSelected(isOptmizeImportsOptionOn());
    myRearrangeEntriesCb.setSelected(LayoutCodeSettingsStorage.getLastSavedRearrangeEntriesCbStateFor(myProject));
    init();
  }

  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @Override
  public boolean isOptimizeImports(){
    return myOptimizeImports.isSelected();
  }

  @Override
  public boolean isProcessOnlyChangedText() {
    return false;
  }

  @Override
  public boolean isRearrangeEntries() {
    return myRearrangeEntriesCb.isSelected();
  }

  @Override
  protected void doOKAction() {
    super.doOKAction();
    PropertiesComponent.getInstance().setValue(LayoutCodeConstants.OPTIMIZE_IMPORTS_KEY, Boolean.toString(myOptimizeImports.isSelected()));
    LayoutCodeSettingsStorage.saveRearrangeEntriesOptionFor(myProject, isRearrangeEntries());
  }

  static boolean isOptmizeImportsOptionOn() {
    return PropertiesComponent.getInstance().getBoolean(LayoutCodeConstants.OPTIMIZE_IMPORTS_KEY, false);
  }
}
