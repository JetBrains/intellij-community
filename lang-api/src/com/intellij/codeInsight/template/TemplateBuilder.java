package com.intellij.codeInsight.template;

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

  /**
   * Creates a replacement box for the specified element with the specified expression.
   *
   * @param element the element to replace.
   * @param expression the replacement expression.
   */
  void replaceElement(PsiElement element, Expression expression);

  /**
   * Shows the live template and initiates editing process.
   */
  void run();
}
