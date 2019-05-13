/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
    @NotNull
    @Override
    public PsiAnnotation[] getAnnotations() {
      return PsiAnnotation.EMPTY_ARRAY;
    }

    @Override
    public String toString() {
      return "EMPTY";
    }
  };

  @NotNull
  PsiAnnotation[] getAnnotations();


  class Static implements TypeAnnotationProvider {
    private final PsiAnnotation[] myAnnotations;

    private Static(PsiAnnotation[] annotations) {
      myAnnotations = annotations;
    }

    @NotNull
    @Override
    public PsiAnnotation[] getAnnotations() {
      return myAnnotations;
    }

    @NotNull
    public static TypeAnnotationProvider create(@NotNull PsiAnnotation[] annotations) {
      return annotations.length == 0 ? EMPTY : new Static(annotations);
    }
  }
}