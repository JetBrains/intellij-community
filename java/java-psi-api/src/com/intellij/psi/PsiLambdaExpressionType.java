// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

/**
 * A type which represents a function denoted by a lambda expression.
 */
public class PsiLambdaExpressionType extends PsiType {
  private final PsiLambdaExpression myExpression;

  public PsiLambdaExpressionType(@NotNull PsiLambdaExpression expression) {
    super(TypeAnnotationProvider.EMPTY);
    myExpression = expression;
  }

  @Override
  public @NotNull String getPresentableText() {
    return getCanonicalText();
  }

  @Override
  public @NotNull String getCanonicalText() {
    return "<lambda expression>";
  }

  @Override
  public boolean isValid() {
    return myExpression.isValid();
  }

  @Override
  public boolean equalsToText(@NotNull String text) {
    return false;
  }

  @Override
  public <A> A accept(@NotNull PsiTypeVisitor<A> visitor) {
    return visitor.visitLambdaExpressionType(this);
  }

  @Override
  public GlobalSearchScope getResolveScope() {
    return null;
  }

  @Override
  public PsiType @NotNull [] getSuperTypes() {
    return PsiType.EMPTY_ARRAY;
  }

  public PsiLambdaExpression getExpression() {
    return myExpression;
  }

  @Override
  public boolean equals(Object obj) {
    return obj == this || obj instanceof PsiLambdaExpressionType && myExpression.equals(((PsiLambdaExpressionType)obj).myExpression);
  }

  @Override
  public int hashCode() {
    return myExpression.hashCode();
  }
}