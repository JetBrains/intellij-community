// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.impl;

import com.intellij.execution.BeforeRunTask;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.WithoutOwnBeforeRunSteps;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.ide.DataManager;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.HideableDecorator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.List;

public class ConfigurationSettingsEditorWrapper extends SettingsEditor<RunnerAndConfigurationSettings>
  implements BeforeRunStepsPanel.StepsBeforeRunListener {
  public static final DataKey<ConfigurationSettingsEditorWrapper> CONFIGURATION_EDITOR_KEY = DataKey.create("ConfigurationSettingsEditor");
  @NonNls private static final String EXPAND_PROPERTY_KEY = "ExpandBeforeRunStepsPanel";

  private JPanel myComponentPlace;
  private JPanel myWholePanel;

  private JPanel myBeforeLaunchContainer;
  private final BeforeRunStepsPanel myBeforeRunStepsPanel;

  private final ConfigurationSettingsEditor myEditor;
  private final HideableDecorator myDecorator;

  public <T extends SettingsEditor> T selectExecutorAndGetEditor(ProgramRunner runner, Class<T> editorClass) {
    return myEditor.selectExecutorAndGetEditor(runner, editorClass);
  }

  public <T extends SettingsEditor> T selectTabAndGetEditor(Class<T> editorClass) {
    return myEditor.selectTabAndGetEditor(editorClass);
  }

  public ConfigurationSettingsEditorWrapper(@NotNull RunnerAndConfigurationSettings settings) {
    myEditor = new ConfigurationSettingsEditor(settings);
    Disposer.register(this, myEditor);
    myBeforeRunStepsPanel = new BeforeRunStepsPanel(this);
    myDecorator = new HideableDecorator(myBeforeLaunchContainer, "", false) {
      @Override
      protected void on() {
        super.on();
        storeState();
      }

      @Override
      protected void off() {
        super.off();
        storeState();
      }

      private void storeState() {
        PropertiesComponent.getInstance().setValue(EXPAND_PROPERTY_KEY, String.valueOf(isExpanded()));
      }
    };
    myDecorator.setOn(PropertiesComponent.getInstance().getBoolean(EXPAND_PROPERTY_KEY, true));
    myDecorator.setContentComponent(myBeforeRunStepsPanel);
    doReset(settings);
  }

  private void doReset(RunnerAndConfigurationSettings settings) {
    final RunConfiguration runConfiguration = settings.getConfiguration();
    myBeforeRunStepsPanel.doReset(settings);
    myBeforeLaunchContainer.setVisible(!(runConfiguration instanceof WithoutOwnBeforeRunSteps));
  }

  @Override
  @NotNull
  protected JComponent createEditor() {
    myComponentPlace.setLayout(new BorderLayout());
    myComponentPlace.add(myEditor.getComponent(), BorderLayout.CENTER);
    DataManager.registerDataProvider(myWholePanel, new MyDataProvider());
    return myWholePanel;
  }

  @Override
  public void resetEditorFrom(@NotNull final RunnerAndConfigurationSettings settings) {
    myEditor.resetEditorFrom(settings);
    doReset(settings);
  }

  @Override
  public void applyEditorTo(@NotNull final RunnerAndConfigurationSettings settings) throws ConfigurationException {
    myEditor.applyEditorTo(settings);
    doApply(settings);
  }

  @Override
  public RunnerAndConfigurationSettings getSnapshot() throws ConfigurationException {
    RunnerAndConfigurationSettings result = myEditor.getSnapshot();
    doApply(result);
    return result;
  }

  private void doApply(@NotNull RunnerAndConfigurationSettings settings) {
    final RunConfiguration runConfiguration = settings.getConfiguration();
    final RunManagerImpl runManager = ((RunnerAndConfigurationSettingsImpl)settings).getManager();
    runManager.setBeforeRunTasks(runConfiguration, myBeforeRunStepsPanel.getTasks(true));
    RunnerAndConfigurationSettings runManagerSettings = runManager.getSettings(runConfiguration);
    if (runManagerSettings != null) {
      runManagerSettings.setEditBeforeRun(myBeforeRunStepsPanel.needEditBeforeRun());
      runManagerSettings.setActivateToolWindowBeforeRun(myBeforeRunStepsPanel.needActivateToolWindowBeforeRun());
    } else {
      settings.setEditBeforeRun(myBeforeRunStepsPanel.needEditBeforeRun());
      settings.setActivateToolWindowBeforeRun(myBeforeRunStepsPanel.needActivateToolWindowBeforeRun());
    }
  }

  public void addBeforeLaunchStep(BeforeRunTask<?> task) {
    myBeforeRunStepsPanel.addTask(task);
  }

  public List<BeforeRunTask> getStepsBeforeLaunch() {
    return Collections.unmodifiableList(myBeforeRunStepsPanel.getTasks(true));
  }

  @Override
  public void fireStepsBeforeRunChanged() {
    fireEditorStateChanged();
  }

  @Override
  public void titleChanged(String title) {
    myDecorator.setTitle(title);
  }

  private class MyDataProvider implements DataProvider {
    @Nullable
    @Override
    public Object getData(@NonNls String dataId) {
      if (CONFIGURATION_EDITOR_KEY.is(dataId)) {
        return ConfigurationSettingsEditorWrapper.this;
      }
      return null;
    }
  }
}
