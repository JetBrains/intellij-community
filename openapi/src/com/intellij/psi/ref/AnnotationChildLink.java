/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.ref;

import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class AnnotationChildLink extends PsiChildLink<PsiModifierListOwner, PsiAnnotation> {
  private final String myAnnoFqn;

  public AnnotationChildLink(String fqn) {
    myAnnoFqn = fqn;
  }

  public String getAnnotationQualifiedName() {
    return myAnnoFqn;
  }

  @Override
  public PsiAnnotation findLinkedChild(@Nullable PsiModifierListOwner member) {
    if (member == null) return null;

    final PsiModifierList modifierList = member.getModifierList();
    return modifierList != null ? modifierList.findAnnotation(myAnnoFqn) : null;
  }

  @NotNull
  public PsiAnnotation createChild(@NotNull PsiModifierListOwner member) throws IncorrectOperationException {
    final PsiElementFactory factory = JavaPsiFacade.getInstance(member.getProject()).getElementFactory();
    final PsiAnnotation annotation = factory.createAnnotationFromText("@" + myAnnoFqn, member);
    final PsiModifierList modifierList = member.getModifierList();
    assert modifierList != null;
    return (PsiAnnotation)modifierList.add(annotation);
  }
}
