// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.ui;

import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public abstract class RunConfigurationEditorFragment<Settings, C extends JComponent> extends SettingsEditorFragment<Settings, C>{
  public RunConfigurationEditorFragment(String id,
                                        @Nls(capitalization = Nls.Capitalization.Sentence) String name,
                                        @Nls(capitalization = Nls.Capitalization.Sentence) String group,
                                        C component,
                                        int commandLinePosition) {
    super(id, name, group, component, commandLinePosition, (settings, c) -> {}, (settings, c) -> {}, settings -> true);
  }

  public abstract void resetEditorFrom(@NotNull RunnerAndConfigurationSettingsImpl s);

  public abstract void applyEditorTo(@NotNull RunnerAndConfigurationSettingsImpl s);

}
