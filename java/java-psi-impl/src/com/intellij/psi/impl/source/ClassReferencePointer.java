// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
      @NotNull
      @Override
      public PsiJavaCodeReferenceElement retrieveReference() {
        return ref;
      }

      @NotNull
      @Override
      public PsiJavaCodeReferenceElement retrieveNonNullReference() {
        return ref;
      }
    };
  }

}
