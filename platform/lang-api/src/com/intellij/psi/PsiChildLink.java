// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class PsiChildLink<Parent extends PsiElement, Child extends PsiElement> implements PsiRefElementCreator<Parent, Child> {
  
  public abstract @Nullable Child findLinkedChild(@Nullable Parent parent);

  public final @NotNull PsiElementRef<Child> createChildRef(@NotNull Parent parent) {
    final Child existing = findLinkedChild(parent);
    if (existing != null) {
      return PsiElementRef.real(existing);
    }
    return PsiElementRef.imaginary(PsiElementRef.real(parent), this);
  }

  public final @NotNull PsiElementRef<Child> createChildRef(@NotNull PsiElementRef<? extends Parent> parentRef) {
    final Parent parent = parentRef.getPsiElement();
    if (parent != null) {
      final Child existing = findLinkedChild(parent);
      if (existing != null) {
        return PsiElementRef.real(existing);
      }
    }
    return PsiElementRef.imaginary(parentRef, this);
  }

}
