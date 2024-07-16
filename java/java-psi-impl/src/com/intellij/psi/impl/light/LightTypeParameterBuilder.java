// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.light;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LightTypeParameterBuilder extends LightPsiClassBuilder implements PsiTypeParameter {

  private final PsiTypeParameterListOwner myOwner;
  private final int myIndex;

  public LightTypeParameterBuilder(@NlsSafe @NotNull String name, PsiTypeParameterListOwner owner, int index) {
    super(owner, name);
    myOwner = owner;
    myIndex = index;
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitTypeParameter(this);
    }
    else {
      visitor.visitElement(this);
    }
  }
  @Override
  public @Nullable PsiTypeParameterListOwner getOwner() {
    return myOwner;
  }

  @Override
  public int getIndex() {
    return myIndex;
  }

  @Override
  public PsiAnnotation @NotNull [] getAnnotations() {
    return getModifierList().getAnnotations();
  }

  @Override
  public PsiAnnotation @NotNull [] getApplicableAnnotations() {
    return getModifierList().getApplicableAnnotations();
  }

  @Override
  public @Nullable PsiAnnotation findAnnotation(@NotNull @NonNls String qualifiedName) {
    return getModifierList().findAnnotation(qualifiedName);
  }

  @Override
  public @NotNull PsiAnnotation addAnnotation(@NotNull @NonNls String qualifiedName) {
    return getModifierList().addAnnotation(qualifiedName);
  }
}
