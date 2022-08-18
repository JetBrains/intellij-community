// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;


import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public interface RedundantSuppressionDetector {

  /**
   * @return comma separated list of suppress ids configured in this {@code element}
   */
  @Nullable
  String getSuppressionIds(@NotNull PsiElement element);

  /**
   * @return quickfix to remove {@code toolId} suppression from list of suppressions
   */
  @Nullable
  LocalQuickFix createRemoveRedundantSuppressionFix(@NotNull String toolId);

  /**
   * @param elementWithSuppression e.g. comment or @SuppressWarning annotation
   * @param place                  element with currently suppressed warning
   * @return true if {@code place} is suppressed by {@code elementWithSuppression}
   */
  boolean isSuppressionFor(@NotNull PsiElement elementWithSuppression, @NotNull PsiElement place, @NotNull String toolId);

  /**
   * @return range with {@code toolId} to highlight in the editor
   */
  @Nullable
  default TextRange getHighlightingRange(@NotNull PsiElement elementWithSuppression, @NotNull String toolId) {
    String suppressionElementText = elementWithSuppression.getText();
    int idx = StringUtil.indexOfIgnoreCase(suppressionElementText, toolId, 0);
    return idx > 0
           ? new TextRange(idx, idx + toolId.length())
           : new TextRange(0, suppressionElementText.length());
  }
}
