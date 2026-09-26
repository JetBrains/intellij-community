// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.generation.surroundWith;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;


public interface SurroundWithRangeAdjuster {
  ExtensionPointName<SurroundWithRangeAdjuster> EP_NAME = ExtensionPointName.create("com.intellij.codeInsight.surroundWithRangeAdjuster");
  
  @Nullable TextRange adjustSurroundWithRange(PsiFile file, TextRange selectedRange);
  default @Nullable TextRange adjustSurroundWithRange(PsiFile file, TextRange selectedRange, boolean hasSelection) {
    return adjustSurroundWithRange(file, selectedRange);
  }
}
