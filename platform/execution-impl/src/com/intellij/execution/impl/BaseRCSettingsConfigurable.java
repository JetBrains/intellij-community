// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.impl;

import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.options.SettingsEditorConfigurable;
import org.jetbrains.annotations.NotNull;

/**
 * This class provides 'smart' isModified() behavior: it compares original settings with current snapshot by their XML 'externalized' presentations
 */
abstract class BaseRCSettingsConfigurable extends SettingsEditorConfigurable<RunnerAndConfigurationSettings> {
  BaseRCSettingsConfigurable(@NotNull SettingsEditor<RunnerAndConfigurationSettings> editor,
                             @NotNull RunnerAndConfigurationSettings settings) {
    super(editor, settings);
  }

  @Override
  public boolean isModified() {
    try {
      RunnerAndConfigurationSettings original = getSettings();

      final RunManagerImpl runManager = ((RunnerAndConfigurationSettingsImpl)original).getManager();
      if (!original.isTemplate() && !runManager.hasSettings(original)) {
        return true;
      }
      if (!super.isModified()) {
        return false;
      }

      RunnerAndConfigurationSettings snapshot = getEditor().getSnapshot();
      if (isSpecificallyModified() ||
          !RunManagerImplKt.doGetBeforeRunTasks(original.getConfiguration())
            .equals(RunManagerImplKt.doGetBeforeRunTasks(snapshot.getConfiguration()))) {
        return true;
      }
    }
    catch (ConfigurationException e) {
      //ignore
    }
    return super.isModified();
  }

  boolean isSpecificallyModified() {
    return false;
  }
}
