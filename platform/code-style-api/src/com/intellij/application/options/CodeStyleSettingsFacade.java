// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.jetbrains.annotations.NotNull;

public class CodeStyleSettingsFacade {
  private final CodeStyleSettings mySettings;

  public CodeStyleSettingsFacade(CodeStyleSettings settings) {
    mySettings = settings;
  }

  public final int getTabSize(@NotNull FileType fileType) {
    return mySettings.getIndentOptions(fileType).TAB_SIZE;
  }
}
