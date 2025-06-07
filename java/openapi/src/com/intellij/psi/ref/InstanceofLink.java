// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.ref;

import com.intellij.psi.PsiChildLink;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class InstanceofLink<Parent extends PsiElement, Child extends PsiElement, CastTo extends Child> extends PsiChildLink<Parent, CastTo> {
  private final PsiChildLink<Parent, Child> myDelegate;
  private final Class<CastTo> myCastTo;

  private InstanceofLink(PsiChildLink<Parent, Child> delegate, Class<CastTo> castTo) {
    myDelegate = delegate;
    myCastTo = castTo;
  }

  @Override
  public CastTo findLinkedChild(@Nullable Parent parent) {
    final Child existing = myDelegate.findLinkedChild(parent);
    return myCastTo.isInstance(existing) ? (CastTo) existing : null;
  }

  @Override
  public @NotNull CastTo createChild(@NotNull Parent parent) throws IncorrectOperationException {
    return (CastTo) myDelegate.createChild(parent);
  }

  public static <Parent extends PsiElement, Child extends PsiElement, CastTo extends Child> InstanceofLink<Parent, Child, CastTo> create(
    PsiChildLink<Parent, Child> delegate, Class<CastTo> castTo) {
    return new InstanceofLink<>(delegate, castTo);
  }
}
