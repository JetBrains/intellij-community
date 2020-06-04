// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.ui;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.Executor;
import com.intellij.execution.configuration.RunConfigurationExtensionBase;
import com.intellij.execution.configuration.RunConfigurationExtensionsManager;
import com.intellij.execution.configurations.ConfigurationPerRunnerSettings;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public abstract class RunConfigurationFragmentedEditor<Settings extends RunConfigurationBase<?>> extends FragmentedSettingsEditor<Settings> {
  private final static Logger LOG = Logger.getInstance(RunConfigurationFragmentedEditor.class);
  private final Settings myRunConfiguration;
  private final RunConfigurationExtensionsManager<RunConfigurationBase<?>, RunConfigurationExtensionBase<RunConfigurationBase<?>>> myExtensionsManager;

  protected RunConfigurationFragmentedEditor(Settings runConfiguration, RunConfigurationExtensionsManager extensionsManager) {
    myRunConfiguration = runConfiguration;
    myExtensionsManager = extensionsManager;
  }

  @Override
  protected final List<SettingsEditorFragment<Settings, ?>> createFragments() {
    List<SettingsEditorFragment<Settings, ?>> fragments = new ArrayList<>(createRunFragments());
    for (SettingsEditorFragment<RunConfigurationBase<?>, ?> wrapper : myExtensionsManager.createFragments(myRunConfiguration)) {
      fragments.add((SettingsEditorFragment<Settings, ?>)wrapper);
    }
    addRunnerSettingsEditors(fragments);
    return fragments;
  }

  private void addRunnerSettingsEditors(List<SettingsEditorFragment<Settings, ?>> fragments) {
    for (Executor executor : Executor.EXECUTOR_EXTENSION_NAME.getExtensionList()) {
      ProgramRunner<RunnerSettings> runner = ProgramRunner.getRunner(executor.getId(), myRunConfiguration);
      if (runner == null) {
        continue;
      }
      SettingsEditor<ConfigurationPerRunnerSettings> configEditor = myRunConfiguration.getRunnerSettingsEditor(runner);
      SettingsEditor<RunnerSettings> runnerEditor = runner.getSettingsEditor(executor, myRunConfiguration);
      if (configEditor == null && runnerEditor == null) {
        continue;
      }
      JComponent component = new JPanel(new BorderLayout());
      component.setBorder(IdeBorderFactory.createTitledBorder(executor.getStartActionText(), false));
      if (configEditor != null) {
        component.add(configEditor.getComponent(), BorderLayout.CENTER);
      }
      if (runnerEditor != null) {
        component.add(runnerEditor.getComponent(), configEditor == null ? BorderLayout.CENTER : BorderLayout.SOUTH);
      }
      RunConfigurationEditorFragment<Settings, JComponent> fragment =
        new RunConfigurationEditorFragment<Settings, JComponent>(executor.getId() + ".config", executor.getStartActionText(),
                                                                 ExecutionBundle.message("run.configuration.startup.connection.rab.title"),
                                                                 component, 0) {
          @Override
          public void resetEditorFrom(@NotNull RunnerAndConfigurationSettingsImpl s) {
            if (configEditor != null) {
              configEditor.resetFrom(s.getConfigurationSettings(runner));
            }
            if (runnerEditor != null) {
              runnerEditor.resetFrom(s.getRunnerSettings(runner));
            }
          }

          @Override
          public void applyEditorTo(@NotNull RunnerAndConfigurationSettingsImpl s) {
            try {
              if (configEditor != null) {
                configEditor.applyTo(s.getConfigurationSettings(runner));
              }
              if (runnerEditor != null) {
                runnerEditor.applyTo(s.getRunnerSettings(runner));
              }
            }
            catch (ConfigurationException e) {
              LOG.error(e);
            }
          }
        };
      if (configEditor != null) {
        Disposer.register(fragment, configEditor);
      }
      if (runnerEditor != null) {
        Disposer.register(fragment, runnerEditor);
      }
      fragments.add(fragment);
    }
  }

  protected abstract List<SettingsEditorFragment<Settings, ?>> createRunFragments();

  public void resetEditorFrom(@NotNull RunnerAndConfigurationSettingsImpl s) {
    for (RunConfigurationEditorFragment<?,?> fragment : getRunFragments()) {
      fragment.resetEditorFrom(s);
    }
  }

  public void applyEditorTo(@NotNull RunnerAndConfigurationSettingsImpl s) {
    for (RunConfigurationEditorFragment<?, ?> fragment : getRunFragments()) {
      fragment.applyEditorTo(s);
    }
  }

  @NotNull
  private List<@NotNull RunConfigurationEditorFragment<?,?>> getRunFragments() {
    return ContainerUtil.mapNotNull(getFragments(),
                                    fragment -> fragment instanceof RunConfigurationEditorFragment
                                                ? (RunConfigurationEditorFragment<?,?>)fragment
                                                : null);
  }
}
