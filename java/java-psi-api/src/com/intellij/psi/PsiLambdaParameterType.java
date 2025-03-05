// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

/**
 * A type which represents an omitted type for parameter of lambda expression.
 */
public class PsiLambdaParameterType extends PsiType {
  private final PsiParameter myParameter;

  public PsiLambdaParameterType(@NotNull PsiParameter parameter) {
    super(TypeAnnotationProvider.EMPTY);
    myParameter = parameter;
  }

  @Override
  public @NotNull String getPresentableText() {
    return getCanonicalText();
  }

  @Override
  public @NotNull String getCanonicalText() {
    return "<lambda parameter>";
  }

  @Override
  public boolean isValid() {
    return myParameter.isValid();
  }

  @Override
  public boolean equalsToText(@NotNull String text) {
    return false;
  }

  @Override
  public <A> A accept(@NotNull PsiTypeVisitor<A> visitor) {
    return visitor.visitType(this);
  }

  @Override
  public GlobalSearchScope getResolveScope() {
    return null;
  }

  @Override
  public PsiType @NotNull [] getSuperTypes() {
    return PsiType.EMPTY_ARRAY;
  }

  public PsiParameter getParameter() {
    return myParameter;
  }

  @Override
  public boolean equals(Object o) {
    return this == o || o instanceof PsiLambdaParameterType && myParameter.equals(((PsiLambdaParameterType)o).myParameter);
  }

  @Override
  public int hashCode() {
    return myParameter.hashCode();
  }
}