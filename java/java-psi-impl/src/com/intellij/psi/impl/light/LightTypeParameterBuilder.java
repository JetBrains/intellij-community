// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.light;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.PsiTypeParameterListOwner;
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

  @Nullable
  @Override
  public PsiTypeParameterListOwner getOwner() {
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

  @Nullable
  @Override
  public PsiAnnotation findAnnotation(@NotNull @NonNls String qualifiedName) {
    return getModifierList().findAnnotation(qualifiedName);
  }

  @NotNull
  @Override
  public PsiAnnotation addAnnotation(@NotNull @NonNls String qualifiedName) {
    return getModifierList().addAnnotation(qualifiedName);
  }
}
