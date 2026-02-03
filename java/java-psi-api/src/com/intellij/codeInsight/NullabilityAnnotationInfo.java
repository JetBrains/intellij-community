// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiModifierListOwner;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

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
  public @NotNull PsiAnnotation getAnnotation() {
    return myAnnotation;
  }

  /**
   * @return nullability this annotation represents
   */
  public @NotNull Nullability getNullability() {
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

  /**
   * Converts this object to {@link TypeNullability}. Inheritance information is lost, as it's not applicable to type nullability. 
   */
  @ApiStatus.Experimental
  public @NotNull TypeNullability toTypeNullability() {
    NullabilitySource source;
    if (myContainer) {
      source = new NullabilitySource.ContainerAnnotation(myAnnotation);
    } else {
      source = new NullabilitySource.ExplicitAnnotation(myAnnotation);
    }
    return new TypeNullability(myNullability, source);
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;

    NullabilityAnnotationInfo info = (NullabilityAnnotationInfo)o;
    return myContainer == info.myContainer &&
           myAnnotation.equals(info.myAnnotation) &&
           myNullability == info.myNullability &&
           Objects.equals(myInheritedFrom, info.myInheritedFrom);
  }

  @Override
  public int hashCode() {
    int result = myAnnotation.hashCode();
    result = 31 * result + myNullability.hashCode();
    result = 31 * result + Objects.hashCode(myInheritedFrom);
    result = 31 * result + Boolean.hashCode(myContainer);
    return result;
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
