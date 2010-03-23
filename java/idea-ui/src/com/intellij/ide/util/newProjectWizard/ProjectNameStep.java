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
package com.intellij.ide.util.newProjectWizard;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.ide.highlighter.ProjectFileType;
import com.intellij.ide.util.newProjectWizard.modes.WizardMode;
import com.intellij.ide.util.projectWizard.*;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.projectImport.ProjectFormatPanel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

/**
 * @author Eugene Zhuravlev
 *         Date: Jul 17, 2007
 */
public class ProjectNameStep extends ModuleWizardStep {
  private static final Icon NEW_PROJECT_ICON = IconLoader.getIcon("/newprojectwizard.png");

  private final JPanel myPanel;
  protected final JPanel myAdditionalContentPanel;
  protected NamePathComponent myNamePathComponent;
  protected final WizardContext myWizardContext;
  protected final StepSequence mySequence;
  protected final WizardMode myMode;
  private ProjectFormatPanel myFormatPanel = new ProjectFormatPanel();

  public ProjectNameStep(WizardContext wizardContext, StepSequence sequence, final WizardMode mode) {
    myWizardContext = wizardContext;
    mySequence = sequence;
    myMode = mode;
    myNamePathComponent = new NamePathComponent(
      IdeBundle.message("label.project.name"),
      IdeBundle.message("label.project.files.location"),
      IdeBundle.message("title.select.project.file.directory", IdeBundle.message("project.new.wizard.project.identification")),
      IdeBundle.message("description.select.project.file.directory", StringUtil.capitalize(IdeBundle.message("project.new.wizard.project.identification"))),
      true, false
    );
    final String baseDir = myWizardContext.getProjectFileDirectory();
    final String projectName = myWizardContext.getProjectName();
    final String initialProjectName = projectName != null ? projectName : ProjectWizardUtil.findNonExistingFileName(baseDir, "untitled", "");
    myNamePathComponent.setPath(projectName == null ? (baseDir + File.separator + initialProjectName) : baseDir);
    myNamePathComponent.setNameValue(initialProjectName);
    myNamePathComponent.getNameComponent().setSelectionStart(0);
    myNamePathComponent.getNameComponent().setSelectionEnd(initialProjectName.length());
    myPanel = new JPanel(new GridBagLayout());
    myPanel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEtchedBorder(), BorderFactory.createEmptyBorder(10, 10, 10, 10)));
    myPanel.add(myNamePathComponent, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(0, 4, 0, 0), 0, 0));

    myPanel.add(myFormatPanel.getPanel(), new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(2, 0, 0, 0), 0, 0));

    myNamePathComponent.setVisible(myWizardContext.getProject() == null);
    myFormatPanel.setVisible(myWizardContext.getProject() == null);

    final Object selectedProjectFormat = myFormatPanel.getStorageFormatComboBox().getSelectedItem();
    myNamePathComponent.syncNameToPath(ProjectFormatPanel.DIR_BASED.equals(selectedProjectFormat));

    myFormatPanel.getStorageFormatComboBox().addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final Object o = myFormatPanel.getStorageFormatComboBox().getSelectedItem();
        myNamePathComponent.syncNameToPath(ProjectFormatPanel.DIR_BASED.equals(o));
      }
    });

    myAdditionalContentPanel = new JPanel(new GridBagLayout());
    myPanel.add(myAdditionalContentPanel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 1.0, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));
  }
  
  public JComponent getComponent() {
    return myPanel;
  }

  public void updateDataModel() {
    myWizardContext.setProjectName(getProjectName());
    final String projectFileDirectory = getProjectFileDirectory();
    myWizardContext.setProjectFileDirectory(projectFileDirectory);
    final ProjectBuilder moduleBuilder = myMode.getModuleBuilder();
    myWizardContext.setProjectBuilder(moduleBuilder);
    myFormatPanel.updateData(myWizardContext);
    if (moduleBuilder instanceof SourcePathsBuilder) {
      ((SourcePathsBuilder)moduleBuilder).setContentEntryPath(projectFileDirectory);
    }
  }

  public Icon getIcon() {
    return myWizardContext.getProject() == null ? NEW_PROJECT_ICON : ICON;
  }

  public JComponent getPreferredFocusedComponent() {
    return myNamePathComponent.getNameComponent();
  }

  public String getHelpId() {
    return "reference.dialogs.new.project.fromCode.name";
  }

  public String getProjectFilePath() {
    if (myWizardContext.getProject() == null) {
      if (myFormatPanel.isDefault()) {
        return getProjectFileDirectory() + "/" + myNamePathComponent.getNameValue() + ProjectFileType.DOT_DEFAULT_EXTENSION;
      }
      else {
        return getProjectFileDirectory() + "/" + ".idea";
      }
    }
    else {
      return getProjectFileDirectory() + "/" + myNamePathComponent.getNameValue() + ModuleFileType.DOT_DEFAULT_EXTENSION;
    }
  }

  public String getProjectFileDirectory() {
    return myNamePathComponent.getPath();
  }

  public String getProjectName() {
    return myNamePathComponent.getNameValue();
  }

  public boolean validate() throws ConfigurationException {
    final String name = myNamePathComponent.getNameValue();
    if (name.length() == 0) {
      final ApplicationInfo info = ApplicationManager.getApplication().getComponent(ApplicationInfo.class);
      throw new ConfigurationException(
        IdeBundle.message("prompt.new.project.file.name", info.getVersionName(), myWizardContext.getPresentationName()));
    }

    final String projectFileDirectory = getProjectFileDirectory();
    if (projectFileDirectory.length() == 0) {
      throw new ConfigurationException(IdeBundle.message("prompt.enter.project.file.location", myWizardContext.getPresentationName()));
    }

    final boolean shouldPromptCreation = myNamePathComponent.isPathChangedByUser();
    if (!ProjectWizardUtil
      .createDirectoryIfNotExists(IdeBundle.message("directory.project.file.directory", myWizardContext.getPresentationName()),
                                  projectFileDirectory, shouldPromptCreation)) {
      return false;
    }

    final File file = new File(projectFileDirectory);
    if (!file.canWrite()) {
      throw new ConfigurationException(String.format("Directory '%s' is not writable!\nPlease choose another project location.", projectFileDirectory));
    }

    boolean shouldContinue = true;
    final File projectFile = new File(getProjectFilePath());
    if (projectFile.exists()) {
      int answer = Messages.showYesNoDialog(
        IdeBundle.message("prompt.overwrite.project.file", projectFile.getAbsolutePath(), myWizardContext.getPresentationName()),
        IdeBundle.message("title.file.already.exists"), Messages.getQuestionIcon());
      shouldContinue = (answer == 0);
    }

    return shouldContinue;
  }
}
