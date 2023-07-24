// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a template expression. It consists of a template processor and a template (or a plain string literal
 * or text block instead of a template).
 *
 * @author Bas Leijdekkers
 */
public interface PsiTemplateExpression extends PsiExpression {

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

  enum ArgumentType {
    STRING_LITERAL, TEXT_BLOCK, TEMPLATE
  }
}