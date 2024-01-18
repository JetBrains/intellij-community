// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Shows a live template-like chooser UI over a PSI element and offers the user to replace certain sub-elements of the
 * specified element with values of his/her choice.
 * @see TemplateBuilderFactory
 */
public interface TemplateBuilder {
  /**
   * Creates a replacement box for the specified element with the specified initial value.
   *
   * @param element the element to replace.
   * @param replacementText the initial value for the replacement.
   */
  void replaceElement(@NotNull PsiElement element, @NlsSafe String replacementText);

  void replaceElement(@NotNull PsiElement element, TextRange rangeWithinElement, String replacementText);

  /**
   * Creates a replacement box for the specified element with the specified expression.
   *
   * @param element the element to replace.
   * @param expression the replacement expression.
   */
  void replaceElement(@NotNull PsiElement element, Expression expression);

  void replaceElement(@NotNull PsiElement element, TextRange rangeWithinElement, Expression expression);

  @ApiStatus.Experimental
  void replaceElement(PsiElement element, @NlsSafe String varName, Expression expression, boolean alwaysStopAt);

  @ApiStatus.Experimental
  void replaceElement (PsiElement element, @NlsSafe String varName, String dependantVariableName, boolean alwaysStopAt);


    /**
     * Creates a replacement box for the specified text range within the container element.
     * @param rangeWithinElement range within the container element.
     * @param replacementText the initial value for the replacement.
     */
  void replaceRange(TextRange rangeWithinElement, String replacementText);

  /**
   * Creates a replacement box for the specified text range within the container element.
   * @param rangeWithinElement range within the container element.
   * @param expression the replacement expression.
   */
  void replaceRange(TextRange rangeWithinElement, Expression expression);

  /**
   * Run the template without any interactivity - no UI, no editor is requested.
   * Consider using this method in the backend applications.
   * It simply fills the variables with provided replacements and commit the document.
   * @param inline if true then inline template will be created, regular otherwise
   */
  void runNonInteractively(boolean inline);

  /**
   * Shows the live template and initiates editing process.
   * @param editor editor to use to start editing process.
   * @param inline if true then inline template will be created, regular otherwise
   */
  void run(@NotNull Editor editor, boolean inline);
}
