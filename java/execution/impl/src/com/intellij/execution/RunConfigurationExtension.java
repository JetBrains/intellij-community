/*
 * Copyright 2000-2007 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 31.01.2007
 * Time: 13:56:12
 */
package com.intellij.execution;

import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.options.SettingsEditorGroup;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.ui.LayeredIcon;
import org.jetbrains.annotations.Nullable;
import org.jdom.Element;

import javax.swing.*;

public abstract class RunConfigurationExtension {
  public static final ExtensionPointName<RunConfigurationExtension> EP_NAME = new ExtensionPointName<RunConfigurationExtension>("com.intellij.runConfigurationExtension");
  public abstract void handleStartProcess(final ModuleBasedConfiguration configuration, final OSProcessHandler handler);
  public abstract <T extends ModuleBasedConfiguration & RunJavaConfiguration> SettingsEditor createEditor(T configuration);
  public abstract String getEditorTitle();
  @Nullable
  public abstract <T extends ModuleBasedConfiguration & RunJavaConfiguration> Icon getIcon(T runConfiguration);

  public static <T extends ModuleBasedConfiguration & RunJavaConfiguration> void appendEditors(T configuration, SettingsEditorGroup<T> group) {
    for (RunConfigurationExtension extension : Extensions.getExtensions(EP_NAME)) {
      final SettingsEditor editor = extension.createEditor(configuration);
      if (editor != null) {
        group.addEditor(extension.getEditorTitle(), editor);
      }
    }
  }

  public static <T extends ModuleBasedConfiguration & RunJavaConfiguration> Icon getIcon(T configuration, Icon icon) {
    for (RunConfigurationExtension extension : Extensions.getExtensions(EP_NAME)) {
      final Icon extIcon = extension.getIcon(configuration);
      if (extIcon != null) {
        return LayeredIcon.create(icon, extIcon);
      }
    }
    return icon;
  }

  public abstract <T extends ModuleBasedConfiguration & RunJavaConfiguration> void updateJavaParameters(final T configuration, final JavaParameters params, RunnerSettings runnerSettings);

  public abstract void readExternal(ModuleBasedConfiguration runConfiguration, Element element) throws InvalidDataException;

  public abstract void writeExternal(ModuleBasedConfiguration runConfiguration, Element element) throws WriteExternalException;

  public abstract <T extends ModuleBasedConfiguration & RunJavaConfiguration> void patchConfiguration(T runJavaConfiguration);

  public static <T extends ModuleBasedConfiguration & RunJavaConfiguration> void patchCreatedConfiguration(T configuration) {
    for (RunConfigurationExtension extension : Extensions.getExtensions(EP_NAME)) {
      extension.patchConfiguration(configuration);
    }
  }
}