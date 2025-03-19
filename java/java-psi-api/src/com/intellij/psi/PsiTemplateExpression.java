// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a template expression. It consists of a template processor and a template (or a plain string literal
 * or text block instead of a template).
 *
 * @author Bas Leijdekkers
 */
public interface PsiTemplateExpression extends PsiExpression, PsiCall {

  /**
   * @return the template processor expression.
   */
  @Nullable PsiExpression getProcessor();

  /**
   * Get the argument type, which can be a string literal, text block or template.
   *
   * @return the argument type of this template expression.
   */
  @NotNull ArgumentType getArgumentType();

  /**
   * @return the template argument when the argument type is a template, null otherwise.
   */
  @Nullable PsiTemplate getTemplate();

  /**
   * @return the literal expression argument when the argument type is a string literal or text block, null otherwise.
   */
  @Nullable PsiLiteralExpression getLiteralExpression();

  /**
   * @return the called {@code Processor.process()} method, or the corresponding subclass method, along with the substitutor; 
   * empty resolve result if the method cannot be resolved (e.g., processor has an invalid type)
   */
  @Override
  @NotNull JavaResolveResult resolveMethodGenerics();

  /**
   * @return the called {@code Processor.process()} method, or the corresponding subclass method;
   * null if the method cannot be resolved (e.g., processor has an invalid type)
   */
  @Override
  @Nullable PsiMethod resolveMethod();

  /**
   * @return null, as template expressions have no traditional argument list
   */
  @Override
  @Contract("-> null")
  default @Nullable PsiExpressionList getArgumentList() {
    return null;
  }

  /**
   * Type of the template expression argument
   */
  enum ArgumentType {
    /**
     * Classic string literal (not text block)
     */
    STRING_LITERAL,
    /**
     * Text block string literal
     */
    TEXT_BLOCK,
    /**
     * A template, which can be either in text-block style, or in classic literal style.
     * Use {@link PsiFragment#isTextBlock()} on template fragments to check this
     */
    TEMPLATE
  }
}