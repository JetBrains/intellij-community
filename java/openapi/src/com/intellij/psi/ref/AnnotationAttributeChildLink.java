// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.ref;

import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class AnnotationAttributeChildLink extends PsiChildLink<PsiAnnotation, PsiAnnotationMemberValue> {
  private final String myAttributeName;

  public AnnotationAttributeChildLink(@NotNull @NonNls String attributeName) {
    myAttributeName = attributeName;
  }

  @NotNull
  public String getAttributeName() {
    return myAttributeName;
  }

  @Override
  public PsiAnnotationMemberValue findLinkedChild(@Nullable PsiAnnotation psiAnnotation) {
    if (psiAnnotation == null) return null;

    return psiAnnotation.findDeclaredAttributeValue(myAttributeName);
  }

  @Override
  @NotNull
  public PsiAnnotationMemberValue createChild(@NotNull PsiAnnotation psiAnnotation) throws IncorrectOperationException {
    final PsiExpression nullValue = JavaPsiFacade.getElementFactory(psiAnnotation.getProject()).createExpressionFromText(PsiKeyword.NULL, null);
    psiAnnotation.setDeclaredAttributeValue(myAttributeName, nullValue);
    return Objects.requireNonNull(psiAnnotation.findDeclaredAttributeValue(myAttributeName));
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final AnnotationAttributeChildLink link = (AnnotationAttributeChildLink)o;

    if (!myAttributeName.equals(link.myAttributeName)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myAttributeName.hashCode();
  }
}
