// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.lang.annotation.ProblemGroup;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * See {@link InspectionManager#createProblemDescriptor(PsiElement, String, LocalQuickFix, ProblemHighlightType, boolean)} for method descriptions.
 */
public interface ProblemDescriptor extends CommonProblemDescriptor {
  ProblemDescriptor[] EMPTY_ARRAY = new ProblemDescriptor[0];

  PsiElement getPsiElement();

  PsiElement getStartElement();

  PsiElement getEndElement();

  TextRange getTextRangeInElement();

  /**
   * Returns 0-based line number of the problem.
   */
  int getLineNumber();

  @NotNull
  ProblemHighlightType getHighlightType();

  boolean isAfterEndOfLine();

  /**
   * Sets custom attributes for highlighting the inspection result. Can be used only when the severity of the problem is INFORMATION.
   *
   * @param key the text attributes key for highlighting the result.
   * @since 9.0
   */
  void setTextAttributes(TextAttributesKey key);

  /**
   * @see ProblemGroup
   */
  @Nullable
  ProblemGroup getProblemGroup();

  /**
   * @see ProblemGroup
   */
  void setProblemGroup(@Nullable ProblemGroup problemGroup);

  boolean showTooltip();
}