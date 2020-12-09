// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.codeStyle;

import com.intellij.application.options.CodeStyleAbstractConfigurable;
import com.intellij.application.options.CodeStyleAbstractPanel;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.jetbrains.annotations.Nls;

public class OtherFileTypesCodeStyleConfigurable extends CodeStyleAbstractConfigurable {
  private final OtherFileTypesCodeStyleOptionsForm myOptionsForm;

  public OtherFileTypesCodeStyleConfigurable(CodeStyleSettings currSettings, CodeStyleSettings modelSettings) {
    super(currSettings, modelSettings, getDisplayNameText());
    myOptionsForm = new OtherFileTypesCodeStyleOptionsForm(modelSettings);
  }

  @Override
  protected CodeStyleAbstractPanel createPanel(CodeStyleSettings settings) {
    return myOptionsForm;
  }

  @Override
  public String getHelpTopic() {
        return "settings.editor.codeStyle.other";
      }

  @Nls
  public static String getDisplayNameText() {
    return ApplicationBundle.message("code.style.other.file.types");
  }
}
