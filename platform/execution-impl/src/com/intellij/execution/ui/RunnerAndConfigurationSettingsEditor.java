// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.ui;

import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class RunnerAndConfigurationSettingsEditor extends SettingsEditor<RunnerAndConfigurationSettings> {

  private final SettingsEditor<RunConfiguration> myConfigurationEditor;

  public RunnerAndConfigurationSettingsEditor(RunnerAndConfigurationSettings settings,
                                              SettingsEditor<RunConfiguration> configurationEditor) {
    super(settings.createFactory());
    myConfigurationEditor = configurationEditor;
    myConfigurationEditor.addSettingsEditorListener(editor -> fireEditorStateChanged());
    Disposer.register(this, myConfigurationEditor);
  }

  @Override
  protected void resetEditorFrom(@NotNull RunnerAndConfigurationSettings s) {
    myConfigurationEditor.resetFrom(s.getConfiguration());
  }

  @Override
  protected void applyEditorTo(@NotNull RunnerAndConfigurationSettings s) throws ConfigurationException {
    myConfigurationEditor.applyTo(s.getConfiguration());
  }

  @Override
  protected @NotNull JComponent createEditor() {
    return myConfigurationEditor.getComponent();
  }
}
