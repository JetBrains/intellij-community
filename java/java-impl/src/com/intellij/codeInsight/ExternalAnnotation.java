// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight;

import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiNameValuePair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;

/**
 * Wrapper for info about external annotation.
 */
public class ExternalAnnotation {

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
  @Nullable
  private final PsiNameValuePair[] values;

  public ExternalAnnotation(@NotNull PsiModifierListOwner owner,
                            @NotNull String annotationFQName,
                            @Nullable PsiNameValuePair[] values) {
    this.owner = owner;
    this.annotationFQName = annotationFQName;
    this.values = values;
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
