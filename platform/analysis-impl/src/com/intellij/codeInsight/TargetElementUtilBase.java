// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageExtension;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtilKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class TargetElementUtilBase {

  /**
   * Attempts to adjust the {@code offset} in the {@code file} to point to an {@link #isIdentifierPart(PsiFile, CharSequence, int) identifier},
   * single quote, double quote, closing bracket or parentheses by moving it back by a single character. Does nothing if there are no
   * identifiers around, or the {@code offset} is already in one.
   *
   * @param file language source for the {@link #isIdentifierPart(PsiFile, CharSequence, int)}
   * @see PsiTreeUtilKt#elementsAroundOffsetUp(PsiFile, int)
   */
  public static int adjustOffset(@Nullable PsiFile file, Document document, final int offset) {
    CharSequence text = document.getCharsSequence();
    int correctedOffset = offset;
    int textLength = document.getTextLength();
    if (offset >= textLength) {
      correctedOffset = textLength - 1;
    }
    else if (!isIdentifierPart(file, text, offset)) {
      correctedOffset--;
    }
    if (correctedOffset >= 0) {
      char charAt = text.charAt(correctedOffset);
      if (charAt == '\'' || charAt == '"' || charAt == ')' || charAt == ']' ||
          isIdentifierPart(file, text, correctedOffset)) {
        return correctedOffset;
      }
    }
    return offset;
  }

  /**
   * @return true iff character at the offset may be a part of an identifier.
   * @see Character#isJavaIdentifierPart(char)
   * @see TargetElementEvaluatorEx#isIdentifierPart(PsiFile, CharSequence, int)
   */
  private static boolean isIdentifierPart(@Nullable PsiFile file, CharSequence text, int offset) {
    if (file != null) {
      TargetElementEvaluatorEx evaluator = getElementEvaluatorsEx(file.getLanguage());
      if (evaluator != null && evaluator.isIdentifierPart(file, text, offset)) return true;
    }
    return Character.isJavaIdentifierPart(text.charAt(offset));
  }

  static final LanguageExtension<TargetElementEvaluator> TARGET_ELEMENT_EVALUATOR =
    new LanguageExtension<>("com.intellij.targetElementEvaluator");

  @Nullable
  private static TargetElementEvaluatorEx getElementEvaluatorsEx(@NotNull Language language) {
    TargetElementEvaluator result = TARGET_ELEMENT_EVALUATOR.forLanguage(language);
    return result instanceof TargetElementEvaluatorEx ? (TargetElementEvaluatorEx)result : null;
  }
}
