// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Wrapper for info about external annotation.
 * @param owner Annotation owner
 * @param annotationFQName Annotation name
 * @param values Annotation content
 */
public record ExternalAnnotation(@NotNull PsiModifierListOwner owner, @NotNull String annotationFQName, PsiNameValuePair @Nullable [] values) {

  private static final Logger LOG = Logger.getInstance(ExternalAnnotation.class);

  public ExternalAnnotation {
    LOG.assertTrue(canBeExternallyAnnotated(owner), "Unable to annotate externally element of type " + owner.getClass());
  }

  private static boolean canBeExternallyAnnotated(@Nullable PsiModifierListOwner owner) {
    if (owner instanceof PsiPackage || owner instanceof PsiClass) return true;
    if (owner instanceof PsiParameter) {
      owner = PsiTreeUtil.getParentOfType(owner, PsiMethod.class, true);
    }
    if (owner instanceof PsiField || owner instanceof PsiMethod) {
      return PsiTreeUtil.getParentOfType(owner, PsiClass.class, true) != null;
    }
    return false;
  }

  public PsiNameValuePair[] getValues() {
    return values();
  }
}
