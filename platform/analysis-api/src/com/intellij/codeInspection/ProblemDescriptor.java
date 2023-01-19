// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.lang.annotation.ProblemGroup;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
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
   * Returns the template (text or HTML) from which the editor tooltip text is built.
   * By default, {@link #getDescriptionTemplate()} result is used.
   */
  @NlsContexts.Tooltip
  @NotNull
  default String getTooltipTemplate() {
    return getDescriptionTemplate();
  }

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

  /**
   * Returns the equivalent ProblemDescriptor that could be applied to the
   * non-physical copy of the file used to preview the modification.
   *
   * @param target target non-physical file
   * @return the problem descriptor that could be applied to the non-physical copy of the file.
   */
  default @NotNull ProblemDescriptor getDescriptorForPreview(@NotNull PsiFile target) {
    PsiElement start;
    PsiElement end;
    PsiElement psi;
    try {
      start = PsiTreeUtil.findSameElementInCopy(getStartElement(), target);
      end = PsiTreeUtil.findSameElementInCopy(getEndElement(), target);
      psi = PsiTreeUtil.findSameElementInCopy(getPsiElement(), target);
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (RuntimeException e) {
      throw new RuntimeException("Failed to obtain element copy for preview; descriptor: " + getDescriptionTemplate(), e);
    }
    ProblemDescriptor pd = this;
    return new ProblemDescriptor() {
      //@formatter:off
      @Override public PsiElement getPsiElement() { return psi;}
      @Override public PsiElement getStartElement() { return start;}
      @Override public PsiElement getEndElement() { return end;}
      @Override public TextRange getTextRangeInElement() { return pd.getTextRangeInElement();}
      @Override public int getLineNumber() { return pd.getLineNumber();}
      @Override public @NotNull ProblemHighlightType getHighlightType() { return pd.getHighlightType();}
      @Override public boolean isAfterEndOfLine() { return pd.isAfterEndOfLine();}
      @Override public void setTextAttributes(TextAttributesKey key) {}
      @Override public @Nullable ProblemGroup getProblemGroup() { return pd.getProblemGroup(); }
      @Override public void setProblemGroup(@Nullable ProblemGroup problemGroup) {}
      @Override public boolean showTooltip() { return pd.showTooltip();}
      @Override public @NotNull String getDescriptionTemplate() { return pd.getDescriptionTemplate();}
      @Override public QuickFix<?> @Nullable [] getFixes() { return QuickFix.EMPTY_ARRAY;}
      //@formatter:on
    };
  }
}