// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.light;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiRecordComponent;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class LightCompactConstructorParameter extends LightParameter implements LightRecordMember {
  private final @NotNull PsiRecordComponent myRecordComponent;

  public LightCompactConstructorParameter(@NotNull String name,
                                          @NotNull PsiType type,
                                          @NotNull PsiElement declarationScope,
                                          @NotNull PsiRecordComponent component) {
    super(name, type, declarationScope);
    myRecordComponent = component;
    setModifierList(new LightRecordComponentModifierList(this, myManager, myRecordComponent));
  }

  @Override
  public PsiElement getContext() {
    return getDeclarationScope();
  }

  @Override
  public PsiFile getContainingFile() {
    return getDeclarationScope().getContainingFile();
  }

  @Override
  public @NotNull PsiRecordComponent getRecordComponent() {
    return myRecordComponent;
  }

  @Override
  public int getTextOffset() {
    return myRecordComponent.getTextOffset();
  }

  @Override
  public @NotNull PsiElement getNavigationElement() {
    return myRecordComponent.getNavigationElement();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    return o instanceof LightCompactConstructorParameter &&
           myRecordComponent.equals(((LightCompactConstructorParameter)o).myRecordComponent);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myRecordComponent);
  }
}
