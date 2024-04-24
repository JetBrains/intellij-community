// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.light;

import com.intellij.psi.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class LightTypeParameter extends LightClass implements PsiTypeParameter {
  public LightTypeParameter(final PsiTypeParameter delegate) {
    super(delegate);
  }

  @Override
  public @NotNull PsiTypeParameter getDelegate() {
    return (PsiTypeParameter)super.getDelegate();
  }

  @Override
  public @NotNull PsiElement copy() {
    return new LightTypeParameter(getDelegate());
  }

  @Override
  public void accept(final @NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitTypeParameter(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public PsiTypeParameterListOwner getOwner() {
    return getDelegate().getOwner();
  }

  @Override
  public int getIndex() {
    return getDelegate().getIndex();
  }

  @Override
  public PsiAnnotation @NotNull [] getAnnotations() {
    return getDelegate().getAnnotations();
  }

  @Override
  public PsiAnnotation @NotNull [] getApplicableAnnotations() {
    return getDelegate().getApplicableAnnotations();
  }

  @Override
  public PsiAnnotation findAnnotation(final @NotNull @NonNls String qualifiedName) {
    return getDelegate().findAnnotation(qualifiedName);
  }

  @Override
  public @NotNull PsiAnnotation addAnnotation(final @NotNull @NonNls String qualifiedName) {
    return getDelegate().addAnnotation(qualifiedName);
  }

  public boolean useDelegateToSubstitute() {
    return true;
  }

  @Override
  public String toString() {
    return "PsiTypeParameter:" + getName();
  }
}
