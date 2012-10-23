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
package com.intellij.ide.util.newProjectWizard;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.ide.util.BrowseFilesListener;
import com.intellij.ide.util.newProjectWizard.modes.CreateFromTemplateMode;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.ProjectWizardUtil;
import com.intellij.ide.util.projectWizard.SettingsStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.MnemonicHelper;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.DocumentAdapter;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.io.File;

public class ProjectSettingsStep extends ProjectNameStep {
  private JPanel myModulePanel;
  private JTextField myModuleName;
  private TextFieldWithBrowseButton myModuleContentRoot;
  private TextFieldWithBrowseButton myModuleFileLocation;
  private JPanel myHeader;
  private JPanel myExpertPanel;

  private boolean myModuleNameChangedByUser = false;
  private boolean myModuleNameDocListenerEnabled = true;

  private boolean myContentRootChangedByUser = false;
  private boolean myContentRootDocListenerEnabled = true;

  private boolean myImlLocationChangedByUser = false;
  private boolean myImlLocationDocListenerEnabled = true;


  public ProjectSettingsStep(final WizardContext wizardContext) {
    super(wizardContext, null);
    myAdditionalContentPanel.add(myModulePanel,
                                 new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1, 1, GridBagConstraints.NORTHWEST,
                                                        GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));
    myHeader.setVisible(myWizardContext.isCreatingNewProject() && !isCreateFromTemplateMode());
    myNamePathComponent.getNameComponent().getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        if (!myModuleNameChangedByUser) {
          setModuleName(myNamePathComponent.getNameValue());
        }
      }
    });

    myModuleContentRoot.addBrowseFolderListener(ProjectBundle.message("project.new.wizard.module.content.root.chooser.title"), ProjectBundle.message("project.new.wizard.module.content.root.chooser.description"),
                                                myWizardContext.getProject(), BrowseFilesListener.SINGLE_DIRECTORY_DESCRIPTOR);

    myNamePathComponent.getPathComponent().getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        if (!myContentRootChangedByUser) {
          setModuleContentRoot(myNamePathComponent.getPath());
        }
      }
    });
    myModuleName.getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        if (myModuleNameDocListenerEnabled) {
          myModuleNameChangedByUser = true;
        }
        String path = getDefaultBaseDir(wizardContext);
        final String moduleName = getModuleName();
        if (path.length() > 0 && !Comparing.strEqual(moduleName, myNamePathComponent.getNameValue())) {
          path += "/" + moduleName;
        }
        if (!myContentRootChangedByUser) {
          final boolean f = myModuleNameChangedByUser;
          myModuleNameChangedByUser = true;
          setModuleContentRoot(path);
          myModuleNameChangedByUser = f;
        }
        if (!myImlLocationChangedByUser) {
          setImlFileLocation(path);
        }
      }
    });
    myModuleContentRoot.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        if (myContentRootDocListenerEnabled) {
          myContentRootChangedByUser = true;
        }
        if (!myImlLocationChangedByUser) {
          setImlFileLocation(myModuleContentRoot.getText());
        }
        if (!myModuleNameChangedByUser) {
          final String path = FileUtil.toSystemIndependentName(myModuleContentRoot.getText());
          final int idx = path.lastIndexOf("/");

          boolean f = myContentRootChangedByUser;
          myContentRootChangedByUser = true;

          boolean i = myImlLocationChangedByUser;
          myImlLocationChangedByUser = true;

          setModuleName(idx >= 0 ? path.substring(idx + 1) : "");

          myContentRootChangedByUser = f;
          myImlLocationChangedByUser = i;
        }
      }
    });

    myModuleFileLocation.addBrowseFolderListener(ProjectBundle.message("project.new.wizard.module.file.chooser.title"), ProjectBundle.message("project.new.wizard.module.file.description"),
                                                 myWizardContext.getProject(), BrowseFilesListener.SINGLE_DIRECTORY_DESCRIPTOR);
    myModuleFileLocation.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        if (myImlLocationDocListenerEnabled) {
          myImlLocationChangedByUser = true;
        }
      }
    });
    myNamePathComponent.getPathComponent().getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        if (!myImlLocationChangedByUser) {
          setImlFileLocation(myNamePathComponent.getPath());
        }
      }
    });
    if (wizardContext.isCreatingNewProject()) {
      setModuleName(myNamePathComponent.getNameValue());
      setModuleContentRoot(myNamePathComponent.getPath());
      setImlFileLocation(myNamePathComponent.getPath());
    } else {
      final Project project = wizardContext.getProject();
      assert project != null;
      VirtualFile baseDir = project.getBaseDir();
      if (baseDir != null) { //e.g. was deleted
        final String baseDirPath = baseDir.getPath();
        String moduleName = ProjectWizardUtil.findNonExistingFileName(baseDirPath, "untitled", "");
        String contentRoot = baseDirPath + "/" + moduleName;
        if (!Comparing.strEqual(project.getName(), wizardContext.getProjectName()) && !wizardContext.isCreatingNewProject() && wizardContext.getProjectName() != null) {
          moduleName = ProjectWizardUtil.findNonExistingFileName(wizardContext.getProjectFileDirectory(), wizardContext.getProjectName(), "");
          contentRoot = wizardContext.getProjectFileDirectory();
        }
        setModuleName(moduleName);
        setModuleContentRoot(contentRoot);
        setImlFileLocation(contentRoot);
        myModuleName.select(0, moduleName.length());
      }
    }
  }

  private boolean isCreateFromTemplateMode() {
    return myMode instanceof CreateFromTemplateMode;
  }

  private String getDefaultBaseDir(WizardContext wizardContext) {
    if (wizardContext.isCreatingNewProject()) {
      return myNamePathComponent.getPath();
    } else {
      final Project project = wizardContext.getProject();
      assert project != null;
      final VirtualFile baseDir = project.getBaseDir();
      if (baseDir != null) {
        return baseDir.getPath();
      }
      return "";
    }
  }

  public JComponent getPreferredFocusedComponent() {
    return myWizardContext.isCreatingNewProject() ? super.getPreferredFocusedComponent() : myModuleName;
  }

  private void setImlFileLocation(final String path) {
    myImlLocationDocListenerEnabled = false;
    myModuleFileLocation.setText(FileUtil.toSystemDependentName(path));
    myImlLocationDocListenerEnabled = true;
  }

  private void setModuleContentRoot(final String path) {
    myContentRootDocListenerEnabled = false;
    myModuleContentRoot.setText(FileUtil.toSystemDependentName(path));
    myContentRootDocListenerEnabled = true;
  }

  private void setModuleName(String moduleName) {
    myModuleNameDocListenerEnabled = false;
    myModuleName.setText(moduleName);
    myModuleNameDocListenerEnabled = true;
  }

  public void updateDataModel() {

    super.updateDataModel();

    final ModuleBuilder builder = (ModuleBuilder)myWizardContext.getProjectBuilder();
    assert builder != null;
    final String moduleName = getModuleName();
    builder.setName(moduleName);
    builder.setModuleFilePath(
      FileUtil.toSystemIndependentName(myModuleFileLocation.getText()) + "/" + moduleName + ModuleFileType.DOT_DEFAULT_EXTENSION);
    builder.setContentEntryPath(FileUtil.toSystemIndependentName(myModuleContentRoot.getText()));
  }

  public boolean validate() throws ConfigurationException {
    final String moduleName = getModuleName();
    if (!myWizardContext.isCreatingNewProject()) {
      final String moduleFileDirectory = myModuleFileLocation.getText();
      if (moduleFileDirectory.length() == 0) {
        throw new ConfigurationException("Enter module file location");
      }
      if (moduleName.length() == 0) {
        throw new ConfigurationException("Enter a module name");
      }

      if (!ProjectWizardUtil.createDirectoryIfNotExists(IdeBundle.message("directory.module.file"), moduleFileDirectory,
                                                        myImlLocationChangedByUser)) {
        return false;
      }
      if (!ProjectWizardUtil.createDirectoryIfNotExists(IdeBundle.message("directory.module.content.root"), myModuleContentRoot.getText(),
                                                        myContentRootChangedByUser)) {
        return false;
      }

      File moduleFile = new File(moduleFileDirectory, moduleName + ModuleFileType.DOT_DEFAULT_EXTENSION);
      if (moduleFile.exists()) {
        int answer = Messages.showYesNoDialog(IdeBundle.message("prompt.overwrite.project.file", moduleFile.getAbsolutePath(), IdeBundle.message("project.new.wizard.module.identification")),
                                              IdeBundle.message("title.file.already.exists"), Messages.getQuestionIcon());
        if (answer != 0) {
          return false;
        }
      }
    }
    Project project = myWizardContext.getProject();
    if (project != null) {
      final Module module;
      final ProjectStructureConfigurable fromConfigurable = ProjectStructureConfigurable.getInstance(project);
      if (fromConfigurable != null) {
        module = fromConfigurable.getModulesConfig().getModule(moduleName);
      }
      else {
        module = ModuleManager.getInstance(project).findModuleByName(moduleName);
      }
      if (module != null) {
        throw new ConfigurationException("Module \'" + moduleName + "\' already exist in project. Please, specify another name.");
      }
    }
    return !myWizardContext.isCreatingNewProject() || super.validate();
  }

  protected String getModuleName() {
    return myModuleName.getText().trim();
  }

  @NotNull
  @Override
  public JComponent getSettingsPanel() {
    return myWizardContext.isCreatingNewProject() ? myNamePathComponent : myModulePanel;
  }

  @Override
  public JComponent getExpertSettingsPanel() {
    return myWizardContext.isCreatingNewProject() ? myModulePanel : myExpertPanel.getComponentCount() == 0 ? null : myExpertPanel;
  }

  @Override
  public SettingsStep addField(String label, JComponent field) {
    JComponent settingsPanel = getSettingsPanel();
    new MnemonicHelper().register(settingsPanel);

    JLabel jLabel = new JLabel(label);
    jLabel.setLabelFor(field);
    settingsPanel.add(jLabel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0, 0, GridBagConstraints.WEST,
                                             GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
    settingsPanel.add(field, new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0, GridBagConstraints.NORTHWEST,
                                            GridBagConstraints.NONE, new Insets(0, 0, 0, 0), 0, 0));
    return this;
  }

  @Override
  public SettingsStep addExpertPanel(JComponent panel) {
    JComponent settingsPanel = myExpertPanel;
    new MnemonicHelper().register(settingsPanel);
    settingsPanel.add(panel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0, GridBagConstraints.NORTHWEST,
                                                    GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));
    return this;
  }
}
