/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.execution.configurations;

import com.intellij.execution.runners.JavaProgramRunner;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.JDOMExternalizable;

public interface RunConfiguration extends RunProfile, JDOMExternalizable, Cloneable {
  ConfigurationFactory getFactory();

  void setName(String name);

  SettingsEditor<? extends RunConfiguration> getConfigurationEditor();

  Project getProject();

  ConfigurationType getType();

  JDOMExternalizable createRunnerSettings(ConfigurationInfoProvider provider);

  SettingsEditor<JDOMExternalizable> getRunnerSettingsEditor(JavaProgramRunner runner);

  RunConfiguration clone();
}
