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

package com.intellij.application.options;

import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;

public class CodeStyleSettingsUtilImpl extends CodeStyleSettingsUtil {
  /**
   * Shows code style settings sutable for the project passed. I.e. it shows project code style page if one
   * is configured to use own code style scheme or global one in other case.
   * @param project
   * @return Returns true if settings were modified during editing session.
   */
  public boolean showCodeStyleSettings(Project project, final Class pageToSelect) {
    CodeStyleSettingsManager settingsManager = CodeStyleSettingsManager.getInstance(project);
    CodeStyleSettings savedSettings = settingsManager.getCurrentSettings().clone();
    final CodeStyleSchemesConfigurable configurable = new CodeStyleSchemesConfigurable(project);
    ShowSettingsUtil.getInstance().editConfigurable(project, configurable, new Runnable() {
      public void run() {
        if (pageToSelect != null) {
          configurable.selectPage(pageToSelect);
        }
      }
    });

    return !savedSettings.equals(settingsManager.getCurrentSettings());
  }
}