/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.application.options.codeStyle;

import com.intellij.application.options.CodeStyleAbstractConfigurable;
import com.intellij.application.options.CodeStyleAbstractPanel;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.options.Configurable;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsProvider;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class WrappingAndBracesSettingsProvider extends CodeStyleSettingsProvider {
  @NotNull
  public Configurable createSettingsPage(final CodeStyleSettings settings, final CodeStyleSettings originalSettings) {
    return new CodeStyleAbstractConfigurable(settings, originalSettings, ApplicationBundle.message("wrapping.and.braces")) {
      public Icon getIcon() {
        return StdFileTypes.JAVA.getIcon();
      }

      protected CodeStyleAbstractPanel createPanel(final CodeStyleSettings settings) {
        return new WrappingAndBracesPanel(settings);
      }

      public String getHelpTopic() {
        return "reference.settingsdialog.IDE.globalcodestyle.wrap";
      }
    };
  }

  @Override
  public String getConfigurableDisplayName() {
    return ApplicationBundle.message("wrapping.and.braces");
  }
}