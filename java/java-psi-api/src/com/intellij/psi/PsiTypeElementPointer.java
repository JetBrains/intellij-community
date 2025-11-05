// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Experimental
public interface PsiTypeElementPointer {
  @Nullable
  PsiTypeElement retrieveElement();


  static PsiTypeElementPointer constant(@NotNull PsiTypeElement ref) {
    return new PsiTypeElementPointer() {
      @Override
      public @NotNull PsiTypeElement retrieveElement() {
        return ref;
      }
    };
  }
}
