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

/*
 * User: anna
 * Date: 10-Jul-2007
 */
package com.intellij.ide.util.newProjectWizard.modes;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.util.BrowseFilesListener;
import com.intellij.ide.util.newProjectWizard.StepSequence;
import com.intellij.ide.util.projectWizard.ExistingModuleLoader;
import com.intellij.ide.util.projectWizard.ProjectBuilder;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;

public class ImportImlMode extends WizardMode {
  private TextFieldWithBrowseButton myModulePathFieldPanel;

  @NotNull
  public String getDisplayName(final WizardContext context) {
    return IdeBundle.message("radio.import.existing.module");
  }

  @NotNull
  public String getDescription(final WizardContext context) {
    return IdeBundle.message("prompt.select.module.file.to.import", ApplicationNamesInfo.getInstance().getFullProductName());
  }


  @Nullable
  protected StepSequence createSteps(@NotNull final WizardContext context, @NotNull final ModulesProvider modulesProvider) {
    return null;
  }

  public boolean isAvailable(WizardContext context) {
    return context.getProject() != null;
  }

  public ProjectBuilder getModuleBuilder() {
    return setUpLoader(FileUtil.toSystemIndependentName(myModulePathFieldPanel.getText().trim()));
  }

  public static ExistingModuleLoader setUpLoader(final String moduleFilePath) {
    final ExistingModuleLoader moduleLoader = new ExistingModuleLoader();
    moduleLoader.setModuleFilePath(moduleFilePath);
    final int startIndex = moduleFilePath.lastIndexOf('/');
    final int endIndex = moduleFilePath.lastIndexOf(".");
    if (startIndex >= 0 && endIndex > startIndex + 1) {
      final String name = moduleFilePath.substring(startIndex + 1, endIndex);
      moduleLoader.setName(name);
    }
    return moduleLoader;
  }

  public void dispose() {
    myModulePathFieldPanel = null; //todo
  }

  public JComponent getAdditionalSettings(final WizardContext wizardContext) {
    JTextField tfModuleFilePath = new JTextField();
    final String productName = ApplicationNamesInfo.getInstance().getProductName();
    final String message = IdeBundle.message("prompt.select.module.file.to.import", productName);
    final BrowseFilesListener listener = new BrowseFilesListener(tfModuleFilePath, message, null, new ModuleFileChooserDescriptor()) {
      @Override
      protected VirtualFile getFileToSelect() {
        final VirtualFile fileToSelect = super.getFileToSelect();
        if (fileToSelect != null) {
          return fileToSelect;
        }
        final Project project = wizardContext.getProject();
        return project != null ? project.getBaseDir() : null;
      }
    };
    myModulePathFieldPanel = new TextFieldWithBrowseButton(tfModuleFilePath, listener);
    onChosen(false);
    return myModulePathFieldPanel;
  }

  @Override
  public boolean validate() throws ConfigurationException {
    final String imlPath = myModulePathFieldPanel.getText().trim();
    if (!new File(imlPath).exists()) throw new ConfigurationException("File \'" + imlPath + "\' doesn't exist");
    if (!FileTypeManager.getInstance().getFileTypeByFileName(imlPath).equals(StdFileTypes.IDEA_MODULE)) throw new ConfigurationException("File \'" + imlPath + "\' doesn't contain IDEA module");
    return super.validate();
  }

  public void onChosen(final boolean enabled) {
    UIUtil.setEnabled(myModulePathFieldPanel, enabled, true);
    if (enabled) {
      myModulePathFieldPanel.getTextField().requestFocusInWindow();
    }
  }

  @Override
  public String getShortName() {
    return "Import from *.iml";
  }

  private static class ModuleFileChooserDescriptor extends FileChooserDescriptor {
    public ModuleFileChooserDescriptor() {
      super(true, false, false, false, false, false);
      setHideIgnored(false);
    }

    public boolean isFileVisible(VirtualFile file, boolean showHiddenFiles) {
      final boolean isVisible = super.isFileVisible(file, showHiddenFiles);
      if (!isVisible || file.isDirectory()) {
        return isVisible;
      }
      return StdFileTypes.IDEA_MODULE.equals(file.getFileType());
    }
  }
}