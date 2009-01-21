/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.ref;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.PsiChildLink;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
*/
public class AnnotationAttributeChildLink extends PsiChildLink<PsiAnnotation, PsiNameValuePair> {
  private final String myName;

  public AnnotationAttributeChildLink(String name) {
    myName = name;
  }

  @Override
  public PsiNameValuePair findLinkedChild(@Nullable PsiAnnotation psiAnnotation) {
    if (psiAnnotation == null) return null;

    psiAnnotation.getText();
    for (final PsiNameValuePair attribute : psiAnnotation.getParameterList().getAttributes()) {
      final String attrName = attribute.getName();
      if (attrName == null && "value".equals(myName) || myName.equals(attrName)) {
        return attribute;
      }
    }
    return null;
  }

  @NotNull
  public PsiNameValuePair createChild(@NotNull PsiAnnotation psiAnnotation) throws IncorrectOperationException {
    psiAnnotation.getText();
    throw new UnsupportedOperationException("Method createChild is not yet implemented in " + getClass().getName());
  }

}
