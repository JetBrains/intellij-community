// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.newProjectWizard;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.IdeCoreBundle;
import com.intellij.ide.JavaUiBundle;
import com.intellij.ide.util.newProjectWizard.modes.WizardMode;
import com.intellij.ide.util.projectWizard.*;
import com.intellij.ide.util.projectWizard.importSources.impl.ProjectFromSourcesBuilderImpl;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.projectImport.ProjectFormatPanel;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;

/**
 * @author Eugene Zhuravlev
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
      JavaUiBundle.message("title.select.project.file.directory", IdeCoreBundle.message("project.new.wizard.project.identification")),
      JavaUiBundle.message("description.select.project.file.directory", StringUtil.capitalize(IdeCoreBundle.message("project.new.wizard.project.identification"))),
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
      myNamePathComponent.add(new JLabel(JavaUiBundle.message("label.project.format")),
                              new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 0.0, 0.0, GridBagConstraints.WEST,
                                                     GridBagConstraints.NONE, JBInsets.emptyInsets(), 0, 0));
      myNamePathComponent.add(myFormatPanel.getStorageFormatComboBox(),
                              new GridBagConstraints(1, GridBagConstraints.RELATIVE, 1, 1, 1.0, 0.0, GridBagConstraints.NORTHWEST,
                                                     GridBagConstraints.NONE, JBInsets.emptyInsets(), 0, 0));
    }

    myNamePathComponent.setVisible(isStepVisible());
    myAdditionalContentPanel = new JPanel(new GridBagLayout());
    myPanel.add(myAdditionalContentPanel, new GridBagConstraints(0, GridBagConstraints.RELATIVE, 1, 1, 1.0, 1.0, GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH,
                                                                 JBInsets.emptyInsets(), 0, 0));
  }

  @Override
  public JComponent getComponent() {
    return myPanel;
  }

  @Override
  public boolean isStepVisible() {
    return myWizardContext.getProject() == null;
  }

  @Override
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

  @Override
  public Icon getIcon() {
    return myWizardContext.getStepIcon();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myNamePathComponent.getNameComponent();
  }

  @Override
  public String getHelpId() {
    return "reference.dialogs.new.project.fromCode.name";
  }

  public String getProjectFileDirectory() {
    return myNamePathComponent.getPath();
  }

  public String getProjectName() {
    return myNamePathComponent.getNameValue();
  }

  @Override
  public boolean validate() throws ConfigurationException {
    return myNamePathComponent.validateNameAndPath(myWizardContext, myFormatPanel.isDefault());
  }
}
