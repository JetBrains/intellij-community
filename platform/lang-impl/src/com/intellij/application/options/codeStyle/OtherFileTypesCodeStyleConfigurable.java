// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.codeStyle;

import com.intellij.application.options.CodeStyleAbstractConfigurable;
import com.intellij.application.options.CodeStyleAbstractPanel;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public final class OtherFileTypesCodeStyleConfigurable extends CodeStyleAbstractConfigurable {
  private final OtherFileTypesCodeStyleOptionsForm myOptionsForm;

  public OtherFileTypesCodeStyleConfigurable(CodeStyleSettings currSettings, CodeStyleSettings modelSettings) {
    super(currSettings, modelSettings, getDisplayNameText());
    myOptionsForm = new OtherFileTypesCodeStyleOptionsForm(modelSettings);
  }

  @Override
  protected @NotNull CodeStyleAbstractPanel createPanel(@NotNull CodeStyleSettings settings) {
    return myOptionsForm;
  }

  @Override
  public String getHelpTopic() {
        return "settings.editor.codeStyle.other";
      }

  public static @Nls String getDisplayNameText() {
    return ApplicationBundle.message("code.style.other.file.types");
  }
}
