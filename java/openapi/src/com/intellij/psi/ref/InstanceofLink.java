/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.ref;

import com.intellij.psi.PsiChildLink;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class InstanceofLink<Parent extends PsiElement, Child extends PsiElement, CastTo extends Child> extends PsiChildLink<Parent, CastTo> {
  private final PsiChildLink<Parent, Child> myDelegate;
  private final Class<CastTo> myCastTo;

  private InstanceofLink(PsiChildLink<Parent, Child> delegate, Class<CastTo> castTo) {
    myDelegate = delegate;
    myCastTo = castTo;
  }

  public CastTo findLinkedChild(@Nullable Parent parent) {
    final Child existing = myDelegate.findLinkedChild(parent);
    return myCastTo.isInstance(existing) ? (CastTo) existing : null;
  }

  @NotNull
  public CastTo createChild(@NotNull Parent parent) throws IncorrectOperationException {
    return (CastTo) myDelegate.createChild(parent);
  }

  public static <Parent extends PsiElement, Child extends PsiElement, CastTo extends Child> InstanceofLink<Parent, Child, CastTo> create(
    PsiChildLink<Parent, Child> delegate, Class<CastTo> castTo) {
    return new InstanceofLink<Parent, Child, CastTo>(delegate, castTo);
  }
}
