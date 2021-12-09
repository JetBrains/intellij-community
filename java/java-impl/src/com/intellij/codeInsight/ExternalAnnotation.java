// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;

/**
 * Wrapper for info about external annotation.
 */
public class ExternalAnnotation {

  private static final Logger LOG = Logger.getInstance(ExternalAnnotation.class); 

  /**
   * Annotation owner
   */
  @NotNull
  private final PsiModifierListOwner owner;

  /**
   * Annotation name
   */
  @NotNull
  private final String annotationFQName;

  /**
   * Annotation content
   */
  private final PsiNameValuePair @Nullable [] values;

  public ExternalAnnotation(@NotNull PsiModifierListOwner owner,
                            @NotNull String annotationFQName,
                            PsiNameValuePair @Nullable [] values) {
    LOG.assertTrue(canBeExternallyAnnotated(owner), "Unable to annotate externally element of type " + owner.getClass());
    this.owner = owner;
    this.annotationFQName = annotationFQName;
    this.values = values;
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

  @NotNull
  public PsiModifierListOwner getOwner() {
    return owner;
  }

  @NotNull
  public String getAnnotationFQName() {
    return annotationFQName;
  }

  public PsiNameValuePair[] getValues() {
    return values;
  }

  @Override
  public String toString() {
    return "ExternalAnnotation{" +
           "owner=" + owner +
           ", annotationFQName='" + annotationFQName + '\'' +
           ", values=" + Arrays.toString(values) +
           '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ExternalAnnotation that = (ExternalAnnotation)o;
    return Objects.equals(owner, that.owner) &&
           Objects.equals(annotationFQName, that.annotationFQName) &&
           Arrays.equals(values, that.values);
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(owner, annotationFQName);
    result = 31 * result + Arrays.hashCode(values);
    return result;
  }
}
