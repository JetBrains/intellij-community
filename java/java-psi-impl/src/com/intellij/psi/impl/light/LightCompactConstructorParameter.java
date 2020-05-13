// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.light;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiRecordComponent;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;

public class LightCompactConstructorParameter extends LightParameter implements LightRecordMember {
  private final @NotNull PsiRecordComponent myRecordComponent;

  public LightCompactConstructorParameter(@NotNull String name,
                                          @NotNull PsiType type,
                                          @NotNull PsiElement declarationScope,
                                          @NotNull PsiRecordComponent component) {
    super(name, type, declarationScope);
    myRecordComponent = component;
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
  @NotNull
  public PsiRecordComponent getRecordComponent() {
    return myRecordComponent;
  }

  @Override
  public int getTextOffset() {
    return myRecordComponent.getTextOffset();
  }

  @NotNull
  @Override
  public PsiElement getNavigationElement() {
    return myRecordComponent.getNavigationElement();
  }
}
