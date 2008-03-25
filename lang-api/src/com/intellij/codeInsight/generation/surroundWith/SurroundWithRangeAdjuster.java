package com.intellij.codeInsight.generation.surroundWith;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public interface SurroundWithRangeAdjuster {
  ExtensionPointName<SurroundWithRangeAdjuster> EP_NAME = ExtensionPointName.create("com.intellij.codeInsight.surroundWithRangeAdjuster");
  
  @Nullable TextRange adjustSurroundWithRange(PsiFile file, TextRange selectedRange);
}
