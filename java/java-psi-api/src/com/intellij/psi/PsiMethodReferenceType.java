// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

/**
 * A type which represents a function denoted by a method reference.
 */
public class PsiMethodReferenceType extends PsiType {
  private final PsiMethodReferenceExpression myReference;

  public PsiMethodReferenceType(@NotNull PsiMethodReferenceExpression reference) {
    super(PsiAnnotation.EMPTY_ARRAY);
    myReference = reference;
  }

  @Override
  public @NotNull String getPresentableText() {
    return getCanonicalText();
  }

  @Override
  public @NotNull String getCanonicalText() {
    return "<method reference>";
  }

  @Override
  public boolean isValid() {
    return myReference.isValid();
  }

  @Override
  public boolean equalsToText(@NotNull String text) {
    return false;
  }

  @Override
  public <A> A accept(@NotNull PsiTypeVisitor<A> visitor) {
    return visitor.visitMethodReferenceType(this);
  }

  @Override
  public GlobalSearchScope getResolveScope() {
    return null;
  }

  @Override
  public PsiType @NotNull [] getSuperTypes() {
    return PsiType.EMPTY_ARRAY;
  }

  public PsiMethodReferenceExpression getExpression() {
    return myReference;
  }

  @Override
  public boolean equals(Object obj) {
    return obj == this || obj instanceof PsiMethodReferenceType && myReference.equals(((PsiMethodReferenceType)obj).myReference);
  }

  @Override
  public int hashCode() {
    return myReference.hashCode();
  }
}