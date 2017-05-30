/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.psi.codeStyle.CodeStyleConfigurable;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class OtherFileTypesCodeStyleConfigurable implements CodeStyleConfigurable {
  private CodeStyleSettings myModelSettings;
  private OtherFileTypesCodeStyleOptionsForm myOptionsForm;

  public OtherFileTypesCodeStyleConfigurable(CodeStyleSettings modelSettings) {
    myModelSettings = modelSettings;
    myOptionsForm = new OtherFileTypesCodeStyleOptionsForm(modelSettings);
  }

  @Override
  public void reset(@NotNull CodeStyleSettings settings) {
    myOptionsForm.reset(settings);
  }

  @Override
  public void apply(@NotNull CodeStyleSettings settings) throws ConfigurationException {
    myOptionsForm.apply(settings);
  }

  @Nls
  @Override
  public String getDisplayName() {
    return ApplicationBundle.message("code.style.other.file.types");
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    return myOptionsForm.getPanel();
  }

  @Override
  public boolean isModified() {
    return myOptionsForm.isModified(myModelSettings);
  }

  @Override
  public void apply() throws ConfigurationException {
    apply(myModelSettings);
  }

  @Override
  public String getHelpTopic() {
        return "settings.editor.codeStyle.other";
      }
}
