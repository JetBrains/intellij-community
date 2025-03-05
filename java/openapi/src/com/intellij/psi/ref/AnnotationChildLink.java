// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.ref;

import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AnnotationChildLink extends PsiChildLink<PsiModifierListOwner, PsiAnnotation> {
  private final String myAnnoFqn;

  public AnnotationChildLink(String fqn) {
    myAnnoFqn = fqn;
  }

  public String getAnnotationQualifiedName() {
    return myAnnoFqn;
  }

  public static PsiElementRef<PsiAnnotation> createRef(@NotNull PsiModifierListOwner parent, @NonNls String fqn) {
    return new AnnotationChildLink(fqn).createChildRef(parent);
  }

  @Override
  public PsiAnnotation findLinkedChild(@Nullable PsiModifierListOwner member) {
    if (member == null) return null;

    final PsiModifierList modifierList = member.getModifierList();
    return modifierList != null ? modifierList.findAnnotation(myAnnoFqn) : null;
  }

  @Override
  public @NotNull PsiAnnotation createChild(@NotNull PsiModifierListOwner member) throws IncorrectOperationException {
    final PsiModifierList modifierList = member.getModifierList();
    assert modifierList != null;
    return modifierList.addAnnotation(myAnnoFqn);
  }

  @Override
  public String toString() {
    return "AnnotationChildLink{" + "myAnnoFqn='" + myAnnoFqn + '\'' + '}';
  }
}
