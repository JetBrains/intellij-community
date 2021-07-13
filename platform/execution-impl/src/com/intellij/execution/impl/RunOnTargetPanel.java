// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.util.List;

public class RunOnTargetPanel {

  private final RunnerAndConfigurationSettings mySettings;
  private final Project myProject;
  private final SettingsEditor<RunnerAndConfigurationSettings> myEditor;
  private final JPanel myPanel;
  private final RunOnTargetComboBox myRunOnComboBox;
  private String myDefaultTargetName;
  private final List<ChangeListener> myChangeListeners = new SmartList<>();
  /**
   * Allows to skip "target is changed" event propagation during the execution of {@link #resetRunOnComboBox(String)}.
   */
  private boolean myIsResetOngoing = false;

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
                                                JBUI.emptyInsets(), 0, 0), false);
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
      if (!myIsResetOngoing) {
        fireChangeListeners(new ChangeEvent(e.getSource()));
      }
    });
  }

  private void fireChangeListeners(ChangeEvent e) {
    for (ChangeListener listener : myChangeListeners) {
      listener.stateChanged(e);
    }
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
    myIsResetOngoing = true;
    try {
      myRunOnComboBox.initModel();
      List<TargetEnvironmentConfiguration> configs = TargetEnvironmentsManager.getInstance(myProject).getTargets().resolvedConfigs();
      myRunOnComboBox.addTargets(ContainerUtil.filter(configs, configuration -> {
        return TargetEnvironmentConfigurationKt.getTargetType(configuration).isSystemCompatible();
      }));
      myRunOnComboBox.selectTarget(targetNameToChoose);
    }
    finally {
      myIsResetOngoing = false;
    }
  }

  public void addChangeListener(@NotNull ChangeListener changeListener) {
    myChangeListeners.add(changeListener);
  }
}
