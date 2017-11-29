/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.ide.util.newProjectWizard.modes.WizardMode;
import com.intellij.ide.util.projectWizard.*;
import com.intellij.ide.util.projectWizard.importSources.impl.ProjectFromSourcesBuilderImpl;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.projectImport.ProjectFormatPanel;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;

/**
 * @author Eugene Zhuravlev
 *         Date: Jul 17, 2007
 */
public class ProjectNameStep extends ModuleWizardStep {
  private final JPanel myPanel;
  protected final JPanel myAdditionalContentPanel;
  protected NamePathComponent myNamePathComponent;
  protected final WizardContext myWizardContext;
  @Nullable
  protected final WizardMode myMode;
  private final ProjectFormatPanel myFormatPanel = new ProjectFormatPanel();

  public ProjectNameStep(WizardContext wizardContext, @Nullable final WizardMode mode) {
    myWizardContext = wizardContext;
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
    myNamePathComponent.getNameComponent().select(0, initialProjectName.length());

    myPanel = new JPanel(new GridBagLayout());
    myPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    myPanel.add(myNamePathComponent, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, JBUI.insets(10, 0, 20, 0), 0, 0));

    if (myWizardContext.isCreatingNewProject()) {
      myNamePathComponent.add(new JLabel("Project format:"),
                              new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.WEST,
                                                     GridBagConstraints.NONE, JBUI.emptyInsets(), 0, 0));
      myNamePathComponent.add(myFormatPanel.getStorageFormatComboBox(),
                              new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST,
                                                     GridBagConstraints.NONE, JBUI.emptyInsets(), 0, 0));
    }

    myNamePathComponent.setVisible(isStepVisible());
    myAdditionalContentPanel = new JPanel(new GridBagLayout());
    myPanel.add(myAdditionalContentPanel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 1.0, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH, JBUI.emptyInsets(), 0, 0));
  }
  
  public JComponent getComponent() {
    return myPanel;
  }

  @Override
  public boolean isStepVisible() {
    return myWizardContext.getProject() == null;
  }

  public void updateDataModel() {
    myWizardContext.setProjectName(getProjectName());
    final String projectFileDirectory = getProjectFileDirectory();
    myWizardContext.setProjectFileDirectory(projectFileDirectory);
    ProjectBuilder moduleBuilder = myWizardContext.getProjectBuilder();
    if (moduleBuilder != null) {
      myWizardContext.setProjectBuilder(moduleBuilder);
      if (moduleBuilder instanceof ModuleBuilder) { // no SourcePathsBuilder here !
        ((ModuleBuilder)moduleBuilder).setContentEntryPath(projectFileDirectory);
      }
      else if (moduleBuilder instanceof ProjectFromSourcesBuilderImpl) {
        ((ProjectFromSourcesBuilderImpl)moduleBuilder).setBaseProjectPath(projectFileDirectory);
      }
    }
    myFormatPanel.updateData(myWizardContext);
  }

  public Icon getIcon() {
    return myWizardContext.getStepIcon();
  }

  public JComponent getPreferredFocusedComponent() {
    return myNamePathComponent.getNameComponent();
  }

  public String getHelpId() {
    return "reference.dialogs.new.project.fromCode.name";
  }

  public String getProjectFileDirectory() {
    return myNamePathComponent.getPath();
  }

  public String getProjectName() {
    return myNamePathComponent.getNameValue();
  }

  public boolean validate() throws ConfigurationException {
    return myNamePathComponent.validateNameAndPath(myWizardContext, myFormatPanel.isDefault());
  }
}
