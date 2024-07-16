// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.light;

import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LightClassTypeReference extends LightClassReferenceBase implements PsiJavaCodeReferenceElement {

  private final @NotNull PsiClassType myType;

  private LightClassTypeReference(@NotNull PsiManager manager, @NotNull String text, @NotNull PsiClassType type) {
    super(manager, text);
    myType = type;
  }

  public LightClassTypeReference(@NotNull PsiManager manager, @NotNull PsiClassType type) {
    this(manager, type.getCanonicalText(true), type);
  }

  @Override
  public @Nullable PsiElement resolve() {
    return myType.resolve();
  }

  @Override
  public @NotNull JavaResolveResult advancedResolve(boolean incompleteCode) {
    return myType.resolveGenerics();
  }

  @Override
  public @Nullable String getReferenceName() {
    return myType.getClassName();
  }

  @Override
  public PsiElement copy() {
    return new LightClassTypeReference(myManager, myText, myType);
  }

  @Override
  public boolean isValid() {
    return myType.isValid();
  }

  public @NotNull PsiClassType getType() {
    return myType;
  }

  @Override
  public @NotNull GlobalSearchScope getResolveScope() {
    return myType.getResolveScope();
  }
}
