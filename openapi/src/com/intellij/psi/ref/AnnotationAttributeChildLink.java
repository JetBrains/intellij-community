/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.ref;

import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
*/
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

    psiAnnotation.getText();
    return psiAnnotation.findDeclaredAttributeValue(myAttributeName);
  }

  @NotNull
  public PsiAnnotationMemberValue createChild(@NotNull PsiAnnotation psiAnnotation) throws IncorrectOperationException {
    psiAnnotation.getText();
    final PsiExpression nullValue = JavaPsiFacade.getElementFactory(psiAnnotation.getProject()).createExpressionFromText(PsiKeyword.NULL, null);
    psiAnnotation.setDeclaredAttributeValue(myAttributeName, nullValue);
    return ObjectUtils.assertNotNull(psiAnnotation.findDeclaredAttributeValue(myAttributeName));
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
