// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiModifierListOwner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a particular nullability annotation instance
 */
public class NullabilityAnnotationInfo {
  private final @NotNull PsiAnnotation myAnnotation;
  private final @NotNull Nullability myNullability;
  private final @Nullable PsiModifierListOwner myInheritedFrom;
  private final boolean myContainer;

  public NullabilityAnnotationInfo(@NotNull PsiAnnotation annotation, @NotNull Nullability nullability, boolean container) {
    this(annotation, nullability, null, container);
  }

  NullabilityAnnotationInfo(@NotNull PsiAnnotation annotation,
                            @NotNull Nullability nullability,
                            @Nullable PsiModifierListOwner inheritedFrom,
                            boolean container) {
    myAnnotation = annotation;
    myNullability = nullability;
    myInheritedFrom = inheritedFrom;
    myContainer = container;
  }

  /**
   * @return annotation object (might be synthetic)
   */
  @NotNull
  public PsiAnnotation getAnnotation() {
    return myAnnotation;
  }

  /**
   * @return nullability this annotation represents
   */
  @NotNull
  public Nullability getNullability() {
    return myNullability;
  }

  /**
   * @return true if this annotation is a container annotation (applied to the whole class/package/etc.)
   */
  public boolean isContainer() {
    return myContainer;
  }

  /**
   * @return true if this annotation is an external annotation
   */
  public boolean isExternal() {
    return AnnotationUtil.isExternalAnnotation(myAnnotation);
  }

  /**
   * @return true if this annotation is an inferred annotation
   */
  public boolean isInferred() {
    return AnnotationUtil.isInferredAnnotation(myAnnotation);
  }

  /**
   * @return an element the annotation was inherited from (PsiParameter or PsiMethod), or null if the annotation was not inherited.
   */
  public @Nullable PsiModifierListOwner getInheritedFrom() {
    return myInheritedFrom;
  }

  @NotNull NullabilityAnnotationInfo withInheritedFrom(@Nullable PsiModifierListOwner owner) {
    return new NullabilityAnnotationInfo(myAnnotation, myNullability, owner, myContainer);
  }

  @Override
  public String toString() {
    return "NullabilityAnnotationInfo{" +
           myNullability + "(" + myAnnotation.getQualifiedName() + ")" +
           (myContainer ? ", container" : "") +
           (myInheritedFrom != null ? ", inherited from: " + myInheritedFrom : "") +
           "}";
  }
}
