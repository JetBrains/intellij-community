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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.GotItTooltip;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public abstract class RunConfigurationFragmentedEditor<Settings extends RunConfigurationBase<?>> extends FragmentedSettingsEditor<Settings> {
  private final static Logger LOG = Logger.getInstance(RunConfigurationFragmentedEditor.class);
  private final RunConfigurationExtensionsManager<RunConfigurationBase<?>, RunConfigurationExtensionBase<RunConfigurationBase<?>>> myExtensionsManager;
  private boolean myDefaultSettings;

  protected RunConfigurationFragmentedEditor(Settings runConfiguration, RunConfigurationExtensionsManager extensionsManager) {
    super(runConfiguration);
    myExtensionsManager = extensionsManager;
  }

  public boolean isInplaceValidationSupported() {
    return false;
  }

  @Override
  protected boolean isDefaultSettings() {
    return myDefaultSettings;
  }

  @NotNull
  protected Project getProject() {
    return mySettings.getProject();
  }

  @Override
  protected final List<SettingsEditorFragment<Settings, ?>> createFragments() {
    List<SettingsEditorFragment<Settings, ?>> fragments = new ArrayList<>(createRunFragments());
    for (SettingsEditorFragment<RunConfigurationBase<?>, ?> wrapper : myExtensionsManager.createFragments(mySettings)) {
      fragments.add((SettingsEditorFragment<Settings, ?>)wrapper);
    }
    addRunnerSettingsEditors(fragments);
//    dump fragment ids for FUS
//    String ids = StringUtil.join(ContainerUtil.sorted(ContainerUtil.map(fragments, (f) -> "\"" + f.getId() + "\"")), ",");
    String configId = mySettings.getType().getId();
    for (SettingsEditorFragment<Settings, ?> fragment : fragments) {
      fragment.setConfigId(configId);
    }
    return fragments;
  }

  private void addRunnerSettingsEditors(List<? super SettingsEditorFragment<Settings, ?>> fragments) {
    for (Executor executor : Executor.EXECUTOR_EXTENSION_NAME.getExtensionList()) {
      ProgramRunner<RunnerSettings> runner = ProgramRunner.getRunner(executor.getId(), mySettings);
      if (runner == null) {
        continue;
      }
      SettingsEditor<ConfigurationPerRunnerSettings> configEditor = mySettings.getRunnerSettingsEditor(runner);
      SettingsEditor<RunnerSettings> runnerEditor = runner.getSettingsEditor(executor, mySettings);
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
        new RunConfigurationEditorFragment<>(executor.getId() + ".config", executor.getStartActionText(),
                                             ExecutionBundle.message("run.configuration.startup.connection.rab.title"),
                                             component, 0, settings -> false) {
          @Override
          public void doReset(@NotNull RunnerAndConfigurationSettingsImpl s) {
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
    myDefaultSettings = s.isTemplate();
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

  public void targetChanged(String targetName) {
    SettingsEditorFragment<Settings, ?> targetPathFragment =
      ContainerUtil.find(getFragments(), fragment -> TargetPathFragment.ID.equals(fragment.getId()));
    if (targetPathFragment != null) {
      targetPathFragment.setSelected(targetName != null);
    }
  }

  @Override
  protected void initFragments(Collection<? extends SettingsEditorFragment<Settings, ?>> fragments) {
    for (SettingsEditorFragment<Settings, ?> fragment : fragments) {
      JComponent component = fragment.getEditorComponent();
      if (component == null) continue;
      component.addFocusListener(new FocusListener() {
        @Override
        public void focusGained(FocusEvent e) {}

        @Override
        public void focusLost(FocusEvent e) {
          checkGotIt(fragment);
        }
      });
    }
  }

  private void checkGotIt(SettingsEditorFragment<Settings, ?> fragment) {
    if (!isDefaultSettings() && !fragment.isCanBeHidden() && !fragment.isTag() && StringUtil.isNotEmpty(fragment.getName())) {
      //noinspection unchecked
      Settings clone = (Settings)mySettings.clone();
      fragment.applyEditorTo(clone);
      if (!fragment.isInitiallyVisible(clone)) {
        JComponent component = fragment.getEditorComponent();
        String text = fragment.getName().replace("\u001B", "");
        new GotItTooltip("fragment.hidden." + fragment.getId(), ExecutionBundle.message("gotIt.popup.message", text), fragment).
          withHeader(ExecutionBundle.message("gotIt.popup.title")).
          show(component, (c) -> new Point(GotItTooltip.ARROW_SHIFT, c.getHeight()));
      }
    }
  }
}
