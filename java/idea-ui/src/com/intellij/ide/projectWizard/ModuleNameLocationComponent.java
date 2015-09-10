/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.ide.projectWizard;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.ide.util.BrowseFilesListener;
import com.intellij.ide.util.projectWizard.AbstractModuleBuilder;
import com.intellij.ide.util.projectWizard.NamePathComponent;
import com.intellij.ide.util.projectWizard.ProjectWizardUtil;
import com.intellij.ide.util.projectWizard.WizardContext;
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
import java.io.File;

/**
 * @author nik
 */
public class ModuleNameLocationComponent {
  private final WizardContext myWizardContext;
  private JTextField myModuleName;
  private TextFieldWithBrowseButton myModuleContentRoot;
  private TextFieldWithBrowseButton myModuleFileLocation;
  private JPanel myModulePanel;

  private boolean myModuleNameChangedByUser = false;
  private boolean myModuleNameDocListenerEnabled = true;

  private boolean myContentRootChangedByUser = false;
  private boolean myContentRootDocListenerEnabled = true;

  private boolean myImlLocationChangedByUser = false;
  private boolean myImlLocationDocListenerEnabled = true;

  private boolean myUpdatePathsWhenNameIsChanged;

  public ModuleNameLocationComponent(@NotNull WizardContext wizardContext) {
    myWizardContext = wizardContext;
  }

  public void updateDataModel(AbstractModuleBuilder moduleBuilder) {
    final String moduleName = getModuleName();
    moduleBuilder.setName(moduleName);
    moduleBuilder.setModuleFilePath(
      FileUtil.toSystemIndependentName(myModuleFileLocation.getText()) + "/" + moduleName + ModuleFileType.DOT_DEFAULT_EXTENSION);
    moduleBuilder.setContentEntryPath(FileUtil.toSystemIndependentName(getModuleContentRoot()));
  }

  public JPanel getModulePanel() {
    return myModulePanel;
  }

  public void bindModuleSettings(final NamePathComponent namePathComponent) {
    namePathComponent.getNameComponent().getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        if (!myModuleNameChangedByUser) {
          setModuleName(namePathComponent.getNameValue());
        }
      }
    });

    myModuleContentRoot.addBrowseFolderListener(ProjectBundle.message("project.new.wizard.module.content.root.chooser.title"),
                                                ProjectBundle.message("project.new.wizard.module.content.root.chooser.description"),
                                                myWizardContext.getProject(), BrowseFilesListener.SINGLE_DIRECTORY_DESCRIPTOR);

    namePathComponent.getPathComponent().getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        if (!myContentRootChangedByUser) {
          setModuleContentRoot(namePathComponent.getPath());
        }
      }
    });
    myModuleName.getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        if (!myUpdatePathsWhenNameIsChanged) {
          return;
        }

        if (myModuleNameDocListenerEnabled) {
          myModuleNameChangedByUser = true;
        }
        String path = getDefaultBaseDir(myWizardContext, namePathComponent);
        final String moduleName = getModuleName();
        if (path.length() > 0 && !Comparing.strEqual(moduleName, namePathComponent.getNameValue())) {
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
          setImlFileLocation(getModuleContentRoot());
        }
        if (!myModuleNameChangedByUser) {
          final String path = FileUtil.toSystemIndependentName(getModuleContentRoot());
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

    myModuleFileLocation.addBrowseFolderListener(ProjectBundle.message("project.new.wizard.module.file.chooser.title"),
                                                 ProjectBundle.message("project.new.wizard.module.file.description"),
                                                 myWizardContext.getProject(), BrowseFilesListener.SINGLE_DIRECTORY_DESCRIPTOR);
    myModuleFileLocation.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        if (myImlLocationDocListenerEnabled) {
          myImlLocationChangedByUser = true;
        }
      }
    });
    namePathComponent.getPathComponent().getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        if (!myImlLocationChangedByUser) {
          setImlFileLocation(namePathComponent.getPath());
        }
      }
    });
    myUpdatePathsWhenNameIsChanged = true;
    if (myWizardContext.isCreatingNewProject()) {
      setModuleName(namePathComponent.getNameValue());
      setModuleContentRoot(namePathComponent.getPath());
      setImlFileLocation(namePathComponent.getPath());
    }
    else {
      final Project project = myWizardContext.getProject();
      assert project != null;
      VirtualFile baseDir = project.getBaseDir();
      if (baseDir != null) { //e.g. was deleted
        final String baseDirPath = baseDir.getPath();
        String moduleName = ProjectWizardUtil.findNonExistingFileName(baseDirPath, "untitled", "");
        String contentRoot = baseDirPath + "/" + moduleName;
        if (!Comparing.strEqual(project.getName(), myWizardContext.getProjectName()) &&
            !myWizardContext.isCreatingNewProject() &&
            myWizardContext.getProjectName() != null) {
          moduleName =
            ProjectWizardUtil.findNonExistingFileName(myWizardContext.getProjectFileDirectory(), myWizardContext.getProjectName(), "");
          contentRoot = myWizardContext.getProjectFileDirectory();
          myUpdatePathsWhenNameIsChanged = !myWizardContext.isProjectFileDirectorySetExplicitly();
        }
        setModuleName(moduleName);
        setModuleContentRoot(contentRoot);
        setImlFileLocation(contentRoot);
        myModuleName.select(0, moduleName.length());
      }
    }
  }


  public void validateExistingModuleName(Project project) throws ConfigurationException {
    final String moduleName = getModuleName();
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

  public boolean validateModulePaths() throws ConfigurationException {
    final String moduleName = getModuleName();
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
      int answer = Messages.showYesNoDialog(IdeBundle.message("prompt.overwrite.project.file", moduleFile.getAbsolutePath(),
                                                              IdeBundle.message("project.new.wizard.module.identification")),
                                            IdeBundle.message("title.file.already.exists"), Messages.getQuestionIcon());
      if (answer != Messages.YES) {
        return false;
      }
    }
    return true;
  }

  private String getModuleContentRoot() {
    return myModuleContentRoot.getText();
  }

  private static String getDefaultBaseDir(WizardContext wizardContext, NamePathComponent namePathComponent) {
    if (wizardContext.isCreatingNewProject()) {
      return namePathComponent.getPath();
    }
    else {
      final Project project = wizardContext.getProject();
      assert project != null;
      final VirtualFile baseDir = project.getBaseDir();
      if (baseDir != null) {
        return baseDir.getPath();
      }
      return "";
    }
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

  public void setModuleName(String moduleName) {
    myModuleNameDocListenerEnabled = false;
    myModuleName.setText(moduleName);
    myModuleNameDocListenerEnabled = true;
  }

  public JTextField getModuleNameField() {
    return myModuleName;
  }

  private String getModuleName() {
    return myModuleName.getText().trim();
  }
}
