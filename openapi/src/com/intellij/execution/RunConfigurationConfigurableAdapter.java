/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.execution;

import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.options.SettingsEditorConfigurable;

import javax.swing.*;

public class  RunConfigurationConfigurableAdapter<T extends RunConfiguration> extends SettingsEditorConfigurable<T>{
  public RunConfigurationConfigurableAdapter(SettingsEditor<T> settingsEditor, T configuration) {
    super(settingsEditor, configuration);
 }

  public String getDisplayName() {
    return getSettings().getName();
  }

  public Icon getIcon() {
    return getSettings().getType().getIcon();
  }

  public String getHelpTopic() {
    return null;
  }

  public T getConfiguration() {
    return getSettings();
  }
}