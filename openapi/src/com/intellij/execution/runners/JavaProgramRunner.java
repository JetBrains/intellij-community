/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.execution.runners;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.configurations.*;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.util.JDOMExternalizable;

public interface JavaProgramRunner<Settings extends JDOMExternalizable> {
  Settings createConfigurationData(ConfigurationInfoProvider settingsProvider);

  void patch(JavaParameters javaParameters, RunnerSettings settings) throws ExecutionException;

  AnAction[] createActions(ExecutionResult executionResult);

  RunnerInfo getInfo();

  SettingsEditor<Settings> getSettingsEditor(RunConfiguration configuration);
}