// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.ui;

import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class RunnerAndConfigurationSettingsEditor extends SettingsEditor<RunnerAndConfigurationSettings> {

  private final RunConfigurationFragmentedEditor<RunConfigurationBase<?>> myConfigurationEditor;

  public RunnerAndConfigurationSettingsEditor(RunnerAndConfigurationSettings settings,
                                              RunConfigurationFragmentedEditor<RunConfigurationBase<?>> configurationEditor) {
    super(settings.createFactory());
    myConfigurationEditor = configurationEditor;
    myConfigurationEditor.addSettingsEditorListener(editor -> fireEditorStateChanged());
    Disposer.register(this, myConfigurationEditor);
  }

  @Override
  protected void resetEditorFrom(@NotNull RunnerAndConfigurationSettings s) {
    myConfigurationEditor.resetFrom((RunConfigurationBase<?>)s.getConfiguration());
    myConfigurationEditor.resetEditorFrom((RunnerAndConfigurationSettingsImpl)s);
  }

  @Override
  protected void applyEditorTo(@NotNull RunnerAndConfigurationSettings s) throws ConfigurationException {
    myConfigurationEditor.applyTo((RunConfigurationBase<?>)s.getConfiguration());
    myConfigurationEditor.applyEditorTo((RunnerAndConfigurationSettingsImpl)s);
  }

  @Override
  protected @NotNull JComponent createEditor() {
    return myConfigurationEditor.getComponent();
  }
}
