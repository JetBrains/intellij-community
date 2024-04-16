// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source;

import com.intellij.psi.PsiJavaCodeReferenceElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

interface ClassReferencePointer {
  @Nullable
  PsiJavaCodeReferenceElement retrieveReference();

  @NotNull
  PsiJavaCodeReferenceElement retrieveNonNullReference();

  static ClassReferencePointer constant(@NotNull PsiJavaCodeReferenceElement ref) {
    return new ClassReferencePointer() {
      @Override
      public @NotNull PsiJavaCodeReferenceElement retrieveReference() {
        return ref;
      }

      @Override
      public @NotNull PsiJavaCodeReferenceElement retrieveNonNullReference() {
        return ref;
      }
    };
  }

}
