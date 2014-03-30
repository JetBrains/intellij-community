/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.execution;

import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.options.SettingsEditorConfigurable;

public class  RunConfigurationConfigurableAdapter<T extends RunConfiguration> extends SettingsEditorConfigurable<T>{
  public RunConfigurationConfigurableAdapter(SettingsEditor<T> settingsEditor, T configuration) {
    super(settingsEditor, configuration);
 }

  @Override
  public String getDisplayName() {
    return getSettings().getName();
  }

  @Override
  public String getHelpTopic() {
    return null;
  }

  public T getConfiguration() {
    return getSettings();
  }
}
