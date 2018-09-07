// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.search;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public interface TodoItem {
  @NotNull
  PsiFile getFile();

  @NotNull
  TextRange getTextRange();

  @NotNull
  TodoPattern getPattern();

  @NotNull
  default List<TextRange> getAdditionalTextRanges() {
    return Collections.emptyList();
  }
}
