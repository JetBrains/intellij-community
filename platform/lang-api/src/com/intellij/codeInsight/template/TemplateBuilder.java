package com.intellij.codeInsight.template;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;

/**
 * Shows a live template-like chooser UI over a PSI element and offers the user to replace certain sub-elements of the
 * specified element with values of his/her choice.
 *
 * @since 9.0
 * @author yole
 * @see com.intellij.codeInsight.template.TemplateBuilderFactory
 */
public interface TemplateBuilder {
  /**
   * Creates a replacement box for the specified element with the specified initial value.
   *
   * @param element the element to replace.
   * @param replacementText the initial value for the replacement.
   */
  void replaceElement(PsiElement element, String replacementText);

  void replaceElement(PsiElement element, TextRange rangeWithinElement, String replacementText);

  /**
   * Creates a replacement box for the specified element with the specified expression.
   *
   * @param element the element to replace.
   * @param expression the replacement expression.
   */
  void replaceElement(PsiElement element, Expression expression);

  /**
   * Creates a replacement box for the specified text range within the container element.
   * @param rangeWithinElement range within the container element.
   * @param replacementText the initial value for the replacement.
   */
  void replaceRange(TextRange rangeWithinElement, String replacementText);

  /**
   * Shows the live template and initiates editing process.
   */
  void run();
}
