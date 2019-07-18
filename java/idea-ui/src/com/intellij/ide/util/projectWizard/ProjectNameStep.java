// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.projectWizard;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.ide.highlighter.ProjectFileType;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ui.JBInsets;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.List;

import static com.intellij.openapi.components.StorageScheme.DIRECTORY_BASED;

/**
 * @author Eugene Zhuravlev
 */
public class ProjectNameStep extends ModuleWizardStep {
  private final NamePathComponent myNamePathComponent;
  private final JPanel myPanel;
  private final WizardContext myWizardContext;

  public ProjectNameStep(WizardContext wizardContext) {
    myWizardContext = wizardContext;
    myNamePathComponent = new NamePathComponent(IdeBundle.message("label.project.name"),
                                                IdeBundle.message("label.component.file.location", StringUtil.capitalize(myWizardContext.getPresentationName())),
                                                IdeBundle.message("title.select.project.file.directory", myWizardContext.getPresentationName()),
                                                IdeBundle.message("description.select.project.file.directory", myWizardContext.getPresentationName()));
    myPanel = new JPanel(new GridBagLayout());
    myPanel.setBorder(BorderFactory.createEtchedBorder());

    String appName = ApplicationNamesInfo.getInstance().getFullProductName();
    myPanel.add(new JLabel(IdeBundle.message("label.please.enter.project.name", appName, wizardContext.getPresentationName())),
                new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL,
                                       JBInsets.create(8, 10), 0, 0));

    myPanel.add(myNamePathComponent, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 1.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL,
                                                            JBInsets.create(8, 10), 0, 0));
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myNamePathComponent.getNameComponent();
  }

  @Override
  public String getHelpId() {
    return "reference.dialogs.new.project.import.name";
  }

  @Override
  public JComponent getComponent() {
    return myPanel;
  }

  @Override
  public void updateStep() {
    super.updateStep();
    myNamePathComponent.setPath(FileUtil.toSystemDependentName(myWizardContext.getProjectFileDirectory()));
    String name = myWizardContext.getProjectName();
    if (name == null) {
      List<String> components = StringUtil.split(FileUtil.toSystemIndependentName(myWizardContext.getProjectFileDirectory()), "/");
      if (!components.isEmpty()) {
        name = components.get(components.size()-1);
      }
    }
    myNamePathComponent.setNameValue(name);
    if (name != null) {
      myNamePathComponent.getNameComponent().setSelectionStart(0);
      myNamePathComponent.getNameComponent().setSelectionEnd(name.length());
    }
  }

  @Override
  public void updateDataModel() {
    myWizardContext.setProjectName(getProjectName());
    myWizardContext.setProjectFileDirectory(getProjectFileDirectory());
  }

  @Override
  public Icon getIcon() {
    return myWizardContext.getStepIcon();
  }

  @Override
  public boolean validate() throws ConfigurationException {
    String name = myNamePathComponent.getNameValue();
    if (name.length() == 0) {
      ApplicationNamesInfo info = ApplicationNamesInfo.getInstance();
      throw new ConfigurationException(IdeBundle.message("prompt.new.project.file.name", info.getFullProductName(), myWizardContext.getPresentationName()));
    }

    final String projectFileDirectory = getProjectFileDirectory();
    if (projectFileDirectory.length() == 0) {
      throw new ConfigurationException(IdeBundle.message("prompt.enter.project.file.location", myWizardContext.getPresentationName()));
    }

    final boolean shouldPromptCreation = myNamePathComponent.isPathChangedByUser();
    String prefix = IdeBundle.message("directory.project.file.directory", myWizardContext.getPresentationName());
    if (!ProjectWizardUtil.createDirectoryIfNotExists(prefix, projectFileDirectory, shouldPromptCreation)) {
      return false;
    }

    boolean shouldContinue = true;

    final String path = myWizardContext.isCreatingNewProject() && myWizardContext.getProjectStorageFormat() == DIRECTORY_BASED
                        ? getProjectFileDirectory() + "/" + Project.DIRECTORY_STORE_FOLDER : getProjectFilePath();
    final File projectFile = new File(path);
    if (projectFile.exists()) {
      final String title = myWizardContext.isCreatingNewProject()
                           ? IdeBundle.message("title.new.project")
                           : IdeBundle.message("title.add.module");
      final String message = myWizardContext.isCreatingNewProject() && myWizardContext.getProjectStorageFormat() == DIRECTORY_BASED
                             ? IdeBundle.message("prompt.overwrite.project.folder",
                                                 Project.DIRECTORY_STORE_FOLDER, projectFile.getParentFile().getAbsolutePath())
                             : IdeBundle.message("prompt.overwrite.project.file",
                                                 projectFile.getAbsolutePath(), myWizardContext.getPresentationName());
      int answer = Messages.showYesNoDialog(message, title, Messages.getQuestionIcon());
      shouldContinue = answer == Messages.YES;
    }

    return shouldContinue;
  }

  @NonNls
  public String getProjectFilePath() {
    return getProjectFileDirectory() + "/" + myNamePathComponent.getNameValue()/*myTfProjectName.getText().trim()*/ +
      (myWizardContext.getProject() == null ? ProjectFileType.DOT_DEFAULT_EXTENSION : ModuleFileType.DOT_DEFAULT_EXTENSION);
  }

  public String getProjectFileDirectory() {
    return FileUtil.toSystemIndependentName(myNamePathComponent.getPath());
  }

  public String getProjectName() {
    return myNamePathComponent.getNameValue();
  }

  @Override
  public String getName() {
    return "Name";
  }

  @Override
  public boolean isStepVisible() {
    final ProjectBuilder builder = myWizardContext.getProjectBuilder();
    if (builder != null && builder.isUpdate()) return false;
    return super.isStepVisible();
  }
}
