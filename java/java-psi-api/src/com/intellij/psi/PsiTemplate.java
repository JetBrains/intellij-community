// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Represents a template (i.e. the argument of a template expression) which contains one or more embedded expressions.
 *
 * @author Bas Leijdekkers
 */
public interface PsiTemplate extends PsiElement {

  /**
   * @return the fragments of this template.
   */
  @NotNull List<@NotNull PsiFragment> getFragments();

  /**
   * @return the embedded expression in this template;
   */
  @NotNull List<@NotNull PsiExpression> getEmbeddedExpressions();

}