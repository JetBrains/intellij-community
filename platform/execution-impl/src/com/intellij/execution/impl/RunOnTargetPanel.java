// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.impl;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.RunOnTargetComboBox;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.target.*;
import com.intellij.execution.ui.TargetAwareRunConfigurationEditor;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.ActionLink;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Objects;

public class RunOnTargetPanel {

  private final RunnerAndConfigurationSettings mySettings;
  private final Project myProject;
  private final SettingsEditor<RunnerAndConfigurationSettings> myEditor;
  private final JPanel myPanel;
  private final RunOnTargetComboBox myRunOnComboBox;
  private String myDefaultTargetName;

  public RunOnTargetPanel(RunnerAndConfigurationSettings settings, SettingsEditor<RunnerAndConfigurationSettings> editor) {
    mySettings = settings;
    myProject = settings.getConfiguration().getProject();
    myEditor = editor;
    myRunOnComboBox = new RunOnTargetComboBox(myProject);
    ActionLink actionLink =
      new ActionLink(ExecutionBundle.message("edit.run.configuration.run.configuration.manage.targets.label"), e -> {
        String selectedName = myRunOnComboBox.getSelectedTargetName();
        LanguageRuntimeType<?> languageRuntime = myRunOnComboBox.getDefaultLanguageRuntimeType();
        TargetEnvironmentsConfigurable targetEnvironmentsConfigurable =
          new TargetEnvironmentsConfigurable(myProject, selectedName, languageRuntime);
        if (targetEnvironmentsConfigurable.openForEditing()) {
          resetRunOnComboBox(selectedName);
          setTargetName(selectedName);
        }
      });
    actionLink.setBorder(JBUI.Borders.emptyLeft(5));
    myPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    myPanel.add(myRunOnComboBox);
    myPanel.add(actionLink);
  }

  public void buildUi(JPanel addTo, @Nullable JLabel nameLabel) {
    addTo.setBorder(JBUI.Borders.emptyLeft(5));
    UI.PanelFactory.panel(myPanel)
      .withLabel(ExecutionBundle.message("run.on"))
      .withComment(ExecutionBundle.message("edit.run.configuration.run.configuration.run.on.comment"))
      .addToPanel(addTo, new GridBagConstraints(0, 0, 1, 1, 1.0, 1.0,
                                                GridBagConstraints.NORTHWEST, GridBagConstraints.BOTH,
                                                JBInsets.emptyInsets(), 0, 0), false);
    JLabel runOnLabel = UIUtil.findComponentOfType(addTo, JLabel.class);
    if (runOnLabel != null && nameLabel != null) {
      runOnLabel.setLabelFor(myRunOnComboBox);
      Dimension nameSize = nameLabel.getPreferredSize();
      Dimension runOnSize = runOnLabel.getPreferredSize();
      double width = Math.max(nameSize.getWidth(), runOnSize.getWidth());
      nameLabel.setPreferredSize(new Dimension((int)width, (int)nameSize.getHeight()));
      runOnLabel.setPreferredSize(new Dimension((int)width, (int)runOnSize.getHeight()));
    }

    myRunOnComboBox.addActionListener(e -> {
      String chosenTarget = myRunOnComboBox.getSelectedTargetName();
      if (!StringUtil.equals(myDefaultTargetName, chosenTarget)) {
        setTargetName(chosenTarget);
      }
    });
  }

  public void reset() {
    RunConfiguration configuration = mySettings.getConfiguration();
    boolean targetAware =
      configuration instanceof TargetEnvironmentAwareRunProfile &&
      ((TargetEnvironmentAwareRunProfile)configuration).getDefaultLanguageRuntimeType() != null &&
      RunTargetsEnabled.get();
    myPanel.getParent().setVisible(targetAware);
    if (targetAware) {
      String defaultTargetName = ((TargetEnvironmentAwareRunProfile)configuration).getDefaultTargetName();
      LanguageRuntimeType<?> defaultRuntime = ((TargetEnvironmentAwareRunProfile)configuration).getDefaultLanguageRuntimeType();
      myRunOnComboBox.setDefaultLanguageRuntimeType(defaultRuntime);
      resetRunOnComboBox(defaultTargetName);
      setTargetName(defaultTargetName);
    }
  }

  public void apply() {
    RunConfiguration runConfiguration = mySettings.getConfiguration();
    if (runConfiguration instanceof TargetEnvironmentAwareRunProfile) {
      ((TargetEnvironmentAwareRunProfile)runConfiguration).setDefaultTargetName(myDefaultTargetName);
    }
  }

  boolean isModified() {
    RunConfiguration runConfiguration = mySettings.getConfiguration();
    return runConfiguration instanceof TargetEnvironmentAwareRunProfile &&
           !Objects.equals(myDefaultTargetName, ((TargetEnvironmentAwareRunProfile)runConfiguration).getDefaultTargetName());
  }

  /**
   * Returns the identifier of the currently selected target.
   *
   * @see TargetEnvironmentAwareRunProfile#getDefaultTargetName()
   */
  @Nullable
  public String getDefaultTargetName() {
    return myDefaultTargetName;
  }

  private void setTargetName(String chosenTarget) {
    myDefaultTargetName = chosenTarget;
    if (myEditor instanceof TargetAwareRunConfigurationEditor) {
      ((TargetAwareRunConfigurationEditor)myEditor).targetChanged(chosenTarget);
    }
  }

  private void resetRunOnComboBox(@Nullable String targetNameToChoose) {
    myRunOnComboBox.initModel();
    List<TargetEnvironmentConfiguration> configs = TargetEnvironmentsManager.getInstance(myProject).getTargets().resolvedConfigs();
    myRunOnComboBox.addTargets(ContainerUtil.filter(configs, configuration -> {
      return TargetEnvironmentConfigurationKt.getTargetType(configuration).isSystemCompatible();
    }));
    myRunOnComboBox.selectTarget(targetNameToChoose);
  }
}
