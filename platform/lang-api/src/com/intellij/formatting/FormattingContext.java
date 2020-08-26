// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.formatting;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Represents a context of current formatting operation
 */
public class FormattingContext {
  private final @NotNull PsiElement myPsiElement;
  private final @NotNull TextRange myFormattingRange;
  private final @NotNull CodeStyleSettings myCodeStyleSettings;
  private final @NotNull FormattingMode myFormattingMode;

  private FormattingContext(@NotNull PsiElement psiElement,
                            @NotNull TextRange formattingRange,
                            @NotNull CodeStyleSettings codeStyleSettings,
                            @NotNull FormattingMode formattingMode) {
    myPsiElement = psiElement;
    myFormattingRange = formattingRange;
    myCodeStyleSettings = codeStyleSettings;
    myFormattingMode = formattingMode;
  }

  public @NotNull FormattingContext withPsiElement(@NotNull PsiElement psiElement) {
    // fixme should we overwrite range here?
    return new FormattingContext(psiElement, myFormattingRange, myCodeStyleSettings, myFormattingMode);
  }

  public @NotNull PsiFile getContainingFile() {
    return Objects.requireNonNull(myPsiElement.getContainingFile());
  }

  public @NotNull ASTNode getNode() {
    return myPsiElement.getNode();
  }

  public @NotNull Project getProject() {
    return myPsiElement.getProject();
  }

  /**
   * @return element being formatted
   */
  public @NotNull PsiElement getPsiElement() {
    return myPsiElement;
  }

  /**
   * @return range being formatted. When text is selected in editor, or auto-formatting  performed after some Psi change, returns respective
   * range: selection or psi element. When this is an offset-based formatting, like indentation or spacing computation at offset, returns
   * empty range {@code (offset,offset)}
   * @apiNote returned range is relative to the containing {@link #getContainingFile() file}, not the {@link #getPsiElement() psiElement}
   */
  public @NotNull TextRange getFormattingRange() {
    return myFormattingRange;
  }

  public @NotNull CodeStyleSettings getCodeStyleSettings() {
    return myCodeStyleSettings;
  }

  /**
   * @return {@link FormattingMode type} of formatting operation performed
   */
  public @NotNull FormattingMode getFormattingMode() {
    return myFormattingMode;
  }

  @Override
  public String toString() {
    return "FormattingContext{" +
           "myPsiElement=" + myPsiElement +
           ", myFormattingRange=" + myFormattingRange +
           ", myCodeStyleSettings=" + myCodeStyleSettings +
           ", myFormattingMode=" + myFormattingMode +
           '}';
  }

  public static @NotNull FormattingContext create(@NotNull PsiElement psiElement,
                                                  @NotNull TextRange formattingRange,
                                                  @NotNull CodeStyleSettings codeStyleSettings,
                                                  @NotNull FormattingMode formattingMode) {
    return new FormattingContext(psiElement, formattingRange, codeStyleSettings, formattingMode);
  }

  /**
   * @return formatting context for the full-range of {@code psiElement}
   */
  public static @NotNull FormattingContext create(@NotNull PsiElement psiElement,
                                                  @NotNull CodeStyleSettings codeStyleSettings,
                                                  @NotNull FormattingMode formattingMode) {
    return new FormattingContext(psiElement, psiElement.getTextRange(), codeStyleSettings, formattingMode);
  }

  /**
   * @return formatting context for {@link FormattingMode#REFORMAT re-formatting} of the full range of {@code psiElement}
   */
  public static @NotNull FormattingContext create(@NotNull PsiElement psiElement,
                                                  @NotNull CodeStyleSettings codeStyleSettings) {
    return new FormattingContext(psiElement, psiElement.getTextRange(), codeStyleSettings, FormattingMode.REFORMAT);
  }
}
