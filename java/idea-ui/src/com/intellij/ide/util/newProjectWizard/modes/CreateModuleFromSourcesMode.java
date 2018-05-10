/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.ide.util.newProjectWizard.modes;

import com.intellij.ide.util.projectWizard.ProjectBuilder;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.io.File;

/**
 * @author nik
 */
public class CreateModuleFromSourcesMode extends CreateFromSourcesMode {
  private TextFieldWithBrowseButton myPathPanel;

  @Override
  public boolean isAvailable(WizardContext context) {
    return !context.isCreatingNewProject();
  }

  @Override
  public ProjectBuilder getModuleBuilder() {
    myProjectBuilder.setBaseProjectPath(myPathPanel.getText().trim());
    return myProjectBuilder;
  }

  @Override
  public JComponent getAdditionalSettings(WizardContext wizardContext) {
    myPathPanel = new TextFieldWithBrowseButton();
    final FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
    myPathPanel.addBrowseFolderListener("Select Directory Containing Module Files", null, wizardContext.getProject(), descriptor);
    onChosen(false);
    return myPathPanel;
  }

  @Override
  public boolean validate() throws ConfigurationException {
    final String path = myPathPanel.getText().trim();
    final File file = new File(path);
    if (!file.exists()) {
      throw new ConfigurationException("File \'" + path + "\' doesn't exist");
    }
    if (!file.isDirectory()) {
      throw new ConfigurationException("\'" + path + "\' is not a directory");
    }
    return super.validate();
  }

  @Override
  public void onChosen(final boolean enabled) {
    UIUtil.setEnabled(myPathPanel, enabled, true);
    if (enabled) {
      myPathPanel.getTextField().requestFocusInWindow();
    }
  }

  @Override
  public void dispose() {
    myPathPanel = null;
    super.dispose();
  }
}
