// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle;

import com.intellij.lang.Language;
import org.jetbrains.annotations.NotNull;

public interface CodeStyleSettingsFacade {

  CodeStyleSettingsFacade withLanguage(@NotNull Language language);

  int getTabSize();

  int getIndentSize();

  boolean isSpaceBeforeComma();

  boolean isSpaceAfterComma();

  boolean isSpaceAroundAssignmentOperators();
}
