// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import org.jetbrains.annotations.NotNull;

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
    public PsiAnnotation @NotNull [] getAnnotations() {
      return PsiAnnotation.EMPTY_ARRAY;
    }

    @Override
    public String toString() {
      return "EMPTY";
    }
  };

  PsiAnnotation @NotNull [] getAnnotations();


  final class Static implements TypeAnnotationProvider {
    private final PsiAnnotation[] myAnnotations;

    private Static(PsiAnnotation[] annotations) {
      myAnnotations = annotations;
    }

    @Override
    public PsiAnnotation @NotNull [] getAnnotations() {
      return myAnnotations;
    }

    @NotNull
    public static TypeAnnotationProvider create(PsiAnnotation @NotNull [] annotations) {
      return annotations.length == 0 ? EMPTY : new Static(annotations);
    }
  }
}