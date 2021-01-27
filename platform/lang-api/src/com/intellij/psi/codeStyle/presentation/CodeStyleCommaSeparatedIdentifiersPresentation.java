// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle.presentation;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class CodeStyleCommaSeparatedIdentifiersPresentation extends CodeStyleSettingPresentation {
  public CodeStyleCommaSeparatedIdentifiersPresentation(@NotNull String fieldName,
                                                        @NotNull @Nls String uiName) {
    super(fieldName, uiName);
  }
}
