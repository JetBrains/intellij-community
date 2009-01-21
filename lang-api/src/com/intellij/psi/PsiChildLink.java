/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public abstract class PsiChildLink<Parent extends PsiElement, Child extends PsiElement> implements PsiRefElementCreator<Parent, Child> {
  
  @Nullable public abstract Child findLinkedChild(@Nullable Parent parent);

  @NotNull
  public final PsiRef<Child> createChildRef(@NotNull Parent parent) {
    final Child existing = findLinkedChild(parent);
    if (existing != null) {
      return PsiRef.real(existing);
    }
    return PsiRef.imaginary(PsiRef.real(parent), this);
  }

  @NotNull
  public final PsiRef<Child> createChildRef(@NotNull PsiRef<? extends Parent> parentRef) {
    final Parent parent = parentRef.getPsiElement();
    if (parent != null) {
      final Child existing = findLinkedChild(parent);
      if (existing != null) {
        return PsiRef.real(existing);
      }
    }
    return PsiRef.imaginary(parentRef, this);
  }

}
