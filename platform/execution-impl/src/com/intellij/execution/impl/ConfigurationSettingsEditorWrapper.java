// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.impl;

import com.intellij.execution.BeforeRunTask;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.WithoutOwnBeforeRunSteps;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.RunConfigurationFragmentedEditor;
import com.intellij.execution.ui.RunnerAndConfigurationSettingsEditor;
import com.intellij.execution.ui.TargetAwareRunConfigurationEditor;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.UiDataProvider;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public final class ConfigurationSettingsEditorWrapper extends SettingsEditor<RunnerAndConfigurationSettings>
  implements BeforeRunStepsPanel.StepsBeforeRunListener, TargetAwareRunConfigurationEditor {
  public static final DataKey<ConfigurationSettingsEditorWrapper> CONFIGURATION_EDITOR_KEY = DataKey.create("ConfigurationSettingsEditor");

  private final ConfigurationSettingsEditorPanel content;
  private final RunOnTargetPanel myRunOnTargetPanel;
  private final @Nullable RunConfigurationStorageUi myRCStorageUi;
  private final BeforeRunStepsPanel myBeforeRunStepsPanel;

  private final ConfigurationSettingsEditor myEditor;

  public <T extends SettingsEditor<?>> T selectExecutorAndGetEditor(ProgramRunner<?> runner, Class<T> editorClass) {
    return myEditor.selectExecutorAndGetEditor(runner, editorClass);
  }

  public <T extends SettingsEditor<?>> T selectTabAndGetEditor(Class<T> editorClass) {
    return myEditor.selectTabAndGetEditor(editorClass);
  }

  private ConfigurationSettingsEditorWrapper(@NotNull RunnerAndConfigurationSettings settings,
                                             @NotNull SettingsEditor<RunConfiguration> configurationEditor) {
    Project project = settings.getConfiguration().getProject();
    // RunConfigurationStorageUi for non-template settings is managed by com.intellij.execution.impl.SingleConfigurationConfigurable
    if (!project.isDefault() && settings.isTemplate()) {
      myRCStorageUi = new RunConfigurationStorageUi(project, () -> fireEditorStateChanged());
      content = new ConfigurationSettingsEditorPanel(myRCStorageUi.createComponent());
      myRunOnTargetPanel = new RunOnTargetPanel(settings, this);
      myRunOnTargetPanel.buildUi(content.targetPanel, null);
    }
    else {
      content = new ConfigurationSettingsEditorPanel(null);
      myRCStorageUi = null;
      myRunOnTargetPanel = null;
    }

    myEditor = new ConfigurationSettingsEditor(settings, configurationEditor);
    myEditor.addSettingsEditorListener(editor -> fireStepsBeforeRunChanged());
    Disposer.register(this, myEditor);
    myBeforeRunStepsPanel = new BeforeRunStepsPanel(this);
    content.beforeRunStepsPlaceholder.setComponent(myBeforeRunStepsPanel);
    doReset(settings);
  }

  private void doReset(@NotNull RunnerAndConfigurationSettings settings) {
    myBeforeRunStepsPanel.doReset(settings);
    content.beforeRunStepsRow.visible(!(settings.getConfiguration() instanceof WithoutOwnBeforeRunSteps));
    content.isAllowRunningInParallelCheckBox.setSelected(settings.getConfiguration().isAllowRunningInParallel());
    content.isAllowRunningInParallelCheckBox.setVisible(settings.isTemplate() &&
                                                        settings.getFactory().getSingletonPolicy().isPolicyConfigurable());

    if (myRCStorageUi != null) {
      myRCStorageUi.reset(settings);
      myRunOnTargetPanel.reset();
    }
  }

  @Override
  protected @NotNull JComponent createEditor() {
    content.componentPlace.setLayout(new BorderLayout());
    content.componentPlace.add(myEditor.getComponent(), BorderLayout.CENTER);
    return UiDataProvider.wrapComponent(content.panel, sink -> {
      sink.set(CONFIGURATION_EDITOR_KEY, this);
    });
  }

  @Override
  public boolean isSpecificallyModified() {
    return myRCStorageUi != null && myRCStorageUi.isModified() || myEditor.isSpecificallyModified();
  }

  @Override
  public void resetEditorFrom(final @NotNull RunnerAndConfigurationSettings settings) {
    myEditor.resetEditorFrom(settings);
    doReset(settings);
  }

  @Override
  public void applyEditorTo(final @NotNull RunnerAndConfigurationSettings settings) throws ConfigurationException {
    myEditor.applyEditorTo(settings);
    doApply((RunnerAndConfigurationSettingsImpl)settings, false);

    if (myRunOnTargetPanel != null) {
      // editing a template run configuration
      myRunOnTargetPanel.apply();
    }
  }

  @Override
  public @NotNull RunnerAndConfigurationSettings getSnapshot() throws ConfigurationException {
    RunnerAndConfigurationSettings result = myEditor.getSnapshot();
    doApply((RunnerAndConfigurationSettingsImpl)result, true);
    return result;
  }

  boolean supportsSnapshots() {
    return myEditor.supportsSnapshots();
  }

  private void doApply(@NotNull RunnerAndConfigurationSettingsImpl settings, boolean isSnapshot) {
    final RunConfiguration runConfiguration = settings.getConfiguration();

    List<BeforeRunTask<?>> tasks = ContainerUtil.copyList(myBeforeRunStepsPanel.getTasks());
    RunnerAndConfigurationSettings settingsToApply = null;
    if (isSnapshot) {
      runConfiguration.setBeforeRunTasks(tasks);
    }
    else {
      RunManagerImpl runManager = settings.getManager();
      runManager.setBeforeRunTasks(runConfiguration, tasks);
      settingsToApply = runManager.getSettings(runConfiguration);
    }

    if (settingsToApply == null) {
      settingsToApply = settings;
    }

    settingsToApply.setEditBeforeRun(myBeforeRunStepsPanel.needEditBeforeRun());
    settingsToApply.setActivateToolWindowBeforeRun(myBeforeRunStepsPanel.needActivateToolWindowBeforeRun());
    settingsToApply.setFocusToolWindowBeforeRun(myBeforeRunStepsPanel.needFocusToolWindowBeforeRun());
    if (content.isAllowRunningInParallelCheckBox.isVisible()) {
      settings.getConfiguration().setAllowRunningInParallel(content.isAllowRunningInParallelCheckBox.isSelected());
    }

    if (myRCStorageUi != null) {
      // editing a template run configuration
      myRCStorageUi.apply(settings);
      if (!isSnapshot) {
        myRCStorageUi.reset(settings); // to reset its internal state
      }
    }
  }

  public void addBeforeLaunchStep(@NotNull BeforeRunTask<?> task) {
    myBeforeRunStepsPanel.addTask(task);
  }

  public void replaceBeforeLaunchSteps(@NotNull List<BeforeRunTask<?>> tasks) {
    myBeforeRunStepsPanel.replaceTasks(tasks);
  }

  /**
   * You MUST NOT modify tasks in the returned list.
   */
  public @NotNull List<BeforeRunTask<?>> getStepsBeforeLaunch() {
    return myBeforeRunStepsPanel.getTasks();
  }

  @Override
  public void fireStepsBeforeRunChanged() {
    fireEditorStateChanged();
  }

  @Override
  public void titleChanged(@NotNull String title) {
    content.beforeRunStepsRow.setTitle(title);
  }

  @Override
  public void targetChanged(String targetName) {
    myEditor.targetChanged(targetName);
  }

  public static SettingsEditor<RunnerAndConfigurationSettings> createWrapper(@NotNull RunnerAndConfigurationSettings settings) {
    SettingsEditor<?> configurationEditor = settings.getConfiguration().getConfigurationEditor();
    //noinspection unchecked
    return configurationEditor instanceof RunConfigurationFragmentedEditor<?>
           ? new RunnerAndConfigurationSettingsEditor(settings,
                                                      (RunConfigurationFragmentedEditor<RunConfigurationBase<?>>)configurationEditor)
           : new ConfigurationSettingsEditorWrapper(settings, (SettingsEditor<RunConfiguration>)configurationEditor);
  }
}
