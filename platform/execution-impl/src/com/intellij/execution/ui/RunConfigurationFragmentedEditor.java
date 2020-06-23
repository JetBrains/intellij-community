// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.ui;

import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public abstract class RunConfigurationFragmentedEditor<Settings extends FragmentedSettings> extends FragmentedSettingsEditor<Settings> {

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
