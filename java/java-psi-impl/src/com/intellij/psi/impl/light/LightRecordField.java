// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.light;

import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

public class LightRecordField extends LightField implements LightRecordMember {
  private final @NotNull PsiRecordComponent myRecordComponent;

  public LightRecordField(@NotNull PsiManager manager,
                          @NotNull PsiField field,
                          @NotNull PsiClass containingClass,
                          @NotNull PsiRecordComponent component) {
    super(manager, field, containingClass);
    myRecordComponent = component;
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

  @Override
  public boolean isWritable() {
    return true;
  }

  @Override
  public PsiFile getContainingFile() {
    PsiClass containingClass = getContainingClass();
    if (containingClass == null) return null;
    return containingClass.getContainingFile();
  }

  @Override
  public PsiElement getContext() {
    return getContainingClass();
  }
}
