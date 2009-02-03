/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.ref;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiChildLink;
import com.intellij.util.IncorrectOperationException;
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
    throw new UnsupportedOperationException("Method createChild is not yet implemented in " + getClass().getName());
  }

}
