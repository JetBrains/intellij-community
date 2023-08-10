// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.generation.surroundWith;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;


public interface SurroundWithRangeAdjuster {
  ExtensionPointName<SurroundWithRangeAdjuster> EP_NAME = ExtensionPointName.create("com.intellij.codeInsight.surroundWithRangeAdjuster");
  
  @Nullable TextRange adjustSurroundWithRange(PsiFile file, TextRange selectedRange);
  @Nullable default TextRange adjustSurroundWithRange(PsiFile file, TextRange selectedRange, boolean hasSelection) {
    return adjustSurroundWithRange(file, selectedRange);
  }
}
