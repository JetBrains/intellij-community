// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.ui;

import com.intellij.execution.configuration.RunConfigurationExtensionBase;
import com.intellij.execution.configuration.RunConfigurationExtensionsManager;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public abstract class RunConfigurationFragmentedEditor<Settings extends FragmentedSettings> extends FragmentedSettingsEditor<Settings> {

  private final Settings myRunConfiguration;
  private final RunConfigurationExtensionsManager<RunConfigurationBase<?>, RunConfigurationExtensionBase<RunConfigurationBase<?>>> myExtensionsManager;

  protected RunConfigurationFragmentedEditor(Settings runConfiguration, RunConfigurationExtensionsManager extensionsManager) {
    myRunConfiguration = runConfiguration;
    myExtensionsManager = extensionsManager;
  }

  @Override
  protected final List<SettingsEditorFragment<Settings, ?>> createFragments() {
    List<SettingsEditorFragment<Settings, ?>> fragments = new ArrayList<>(createRunFragments());
    for (SettingsEditorFragment<RunConfigurationBase<?>, ?> wrapper : myExtensionsManager.createFragments((RunConfigurationBase<?>)myRunConfiguration)) {
      fragments.add((SettingsEditorFragment<Settings, ?>)wrapper);
    }
    return fragments;
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
