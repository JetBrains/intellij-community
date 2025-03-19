// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * An object that returns annotations for {@link PsiType}. Since computing type annotations might be computationally expensive sometimes,
 * this object is used to delay the calculation until annotations are really needed,
 * and to pass the annotations without calculating them when creating new types based on existing types.
 *
 * @see PsiType#getAnnotationProvider()
 * @see PsiType#PsiType(TypeAnnotationProvider)
 */
public interface TypeAnnotationProvider {
  TypeAnnotationProvider EMPTY = new TypeAnnotationProvider() {
    @Override
    public @NotNull PsiAnnotation @NotNull [] getAnnotations() {
      return PsiAnnotation.EMPTY_ARRAY;
    }

    @Override
    public String toString() {
      return "EMPTY";
    }
  };

  @NotNull PsiAnnotation @NotNull [] getAnnotations();

  /**
   * @param owner owner for annotations in this provider
   * @return a provider whose annotations are updated to return the supplied owner. 
   * May return itself if changing the owner is not supported, or owner is already set for all the annotations.
   */
  @ApiStatus.Internal
  default @NotNull TypeAnnotationProvider withOwner(@NotNull PsiAnnotationOwner owner) {
    return this;
  }


  final class Static implements TypeAnnotationProvider {
    private final @NotNull PsiAnnotation @NotNull [] myAnnotations;

    private Static(@NotNull PsiAnnotation @NotNull [] annotations) {
      myAnnotations = annotations;
    }

    @Override
    public @NotNull PsiAnnotation @NotNull [] getAnnotations() {
      return myAnnotations;
    }

    public static @NotNull TypeAnnotationProvider create(@NotNull PsiAnnotation @NotNull [] annotations) {
      if (annotations.length == 0) return EMPTY;
      for (PsiAnnotation annotation : annotations) {
        Objects.requireNonNull(annotation);
      }
      return new Static(annotations);
    }
  }
}