// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.impl;

import com.intellij.execution.BeforeRunTask;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.WithoutOwnBeforeRunSteps;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.FragmentedSettings;
import com.intellij.execution.ui.RunConfigurationFragmentedEditor;
import com.intellij.execution.ui.RunnerAndConfigurationSettingsEditor;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.HideableDecorator;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.labels.LinkListener;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class ConfigurationSettingsEditorWrapper extends SettingsEditor<RunnerAndConfigurationSettings>
  implements BeforeRunStepsPanel.StepsBeforeRunListener {
  public static final DataKey<ConfigurationSettingsEditorWrapper> CONFIGURATION_EDITOR_KEY = DataKey.create("ConfigurationSettingsEditor");
  @NonNls private static final String EXPAND_PROPERTY_KEY = "ExpandBeforeRunStepsPanel";

  private JPanel myComponentPlace;
  private JPanel myWholePanel;

  private JPanel myBeforeLaunchContainer;
  private JBCheckBox myIsAllowRunningInParallelCheckBox;
  private JPanel myDisclaimerPanel;
  private JLabel myDisclaimerLabel;
  private JLabel myCreateNewRCLabel;
  private final BeforeRunStepsPanel myBeforeRunStepsPanel;

  private final ConfigurationSettingsEditor myEditor;
  private final HideableDecorator myDecorator;

  public <T extends SettingsEditor> T selectExecutorAndGetEditor(ProgramRunner runner, Class<T> editorClass) {
    return myEditor.selectExecutorAndGetEditor(runner, editorClass);
  }

  public <T extends SettingsEditor> T selectTabAndGetEditor(Class<T> editorClass) {
    return myEditor.selectTabAndGetEditor(editorClass);
  }

  private ConfigurationSettingsEditorWrapper(@NotNull RunnerAndConfigurationSettings settings, SettingsEditor<RunConfiguration> configurationEditor) {
    myEditor = new ConfigurationSettingsEditor(settings, configurationEditor);
    myEditor.addSettingsEditorListener(editor -> fireStepsBeforeRunChanged());
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

  private void doReset(@NotNull RunnerAndConfigurationSettings settings) {
    myBeforeRunStepsPanel.doReset(settings);
    myBeforeLaunchContainer.setVisible(!(settings.getConfiguration() instanceof WithoutOwnBeforeRunSteps));

    myIsAllowRunningInParallelCheckBox.setSelected(settings.getConfiguration().isAllowRunningInParallel());
    myIsAllowRunningInParallelCheckBox.setVisible(settings.isTemplate() && settings.getFactory().getSingletonPolicy().isPolicyConfigurable());

    myDisclaimerPanel.setVisible(settings.isTemplate() && ProjectManager.getInstance().getOpenProjects().length != 0);
  }

  @Override
  @NotNull
  protected JComponent createEditor() {
    myComponentPlace.setLayout(new BorderLayout());
    myComponentPlace.add(myEditor.getComponent(), BorderLayout.CENTER);
    DataManager.registerDataProvider(myWholePanel, dataId -> {
      if (CONFIGURATION_EDITOR_KEY.is(dataId)) {
        return this;
      }
      return null;
    });
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
    doApply((RunnerAndConfigurationSettingsImpl)settings, false);
  }

  @NotNull
  @Override
  public RunnerAndConfigurationSettings getSnapshot() throws ConfigurationException {
    RunnerAndConfigurationSettings result = myEditor.getSnapshot();
    doApply((RunnerAndConfigurationSettingsImpl)result, true);
    return result;
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
    if (myIsAllowRunningInParallelCheckBox.isVisible()) {
      settings.getConfiguration().setAllowRunningInParallel(myIsAllowRunningInParallelCheckBox.isSelected());
    }
  }

  public void addBeforeLaunchStep(@NotNull BeforeRunTask<?> task) {
    myBeforeRunStepsPanel.addTask(task);
  }

  /**
   * You MUST NOT modify tasks in the returned list.
   */
  @NotNull
  public List<BeforeRunTask<?>> getStepsBeforeLaunch() {
    return myBeforeRunStepsPanel.getTasks();
  }

  @Override
  public void fireStepsBeforeRunChanged() {
    fireEditorStateChanged();
  }

  @Override
  public void titleChanged(@NotNull String title) {
    myDecorator.setTitle(title);
  }

  private void createUIComponents() {
    myDisclaimerLabel = new JLabel(ExecutionBundle.message("template.disclaimer"), AllIcons.General.Warning, SwingConstants.LEADING);
    myDisclaimerLabel.setBorder(JBUI.Borders.emptyBottom(2));
    myCreateNewRCLabel = new LinkLabel(ExecutionBundle.message("create.configuration"), null, new LinkListener() {
      @Override
      public void linkSelected(LinkLabel aSource, Object aLinkData) {
        RunConfigurationCreator creator =
          DataManager.getInstance().getDataContext(myDisclaimerLabel).getData(RunConfigurationCreator.KEY);
        if (creator != null) {
          creator.createNewConfiguration(myEditor.getFactory().create().getFactory());
        }
      }
    });
  }

  public static SettingsEditor<RunnerAndConfigurationSettings> createWrapper(@NotNull RunnerAndConfigurationSettings settings) {
    SettingsEditor<?> configurationEditor = settings.getConfiguration().getConfigurationEditor();
    //noinspection unchecked
    return configurationEditor instanceof RunConfigurationFragmentedEditor<?>
           ? new RunnerAndConfigurationSettingsEditor(settings, (RunConfigurationFragmentedEditor<FragmentedSettings>)configurationEditor)
           : new ConfigurationSettingsEditorWrapper(settings, (SettingsEditor<RunConfiguration>)configurationEditor);
  }
}
