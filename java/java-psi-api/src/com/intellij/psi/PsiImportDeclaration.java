// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.util.ArrayFactory;
import org.jetbrains.annotations.Nullable;

public interface PsiImportDeclaration extends PsiElement {
  /**
   * The empty array of PSI base import statements which can be reused to avoid unnecessary allocations.
   */
  PsiImportDeclaration[] EMPTY_ARRAY = new PsiImportDeclaration[0];

  ArrayFactory<PsiImportDeclaration> ARRAY_FACTORY = count -> count == 0 ? EMPTY_ARRAY : new PsiImportDeclaration[count];

  /**
   * Resolves the reference to the imported class, package or member.
   *
   * @return the target element, or null if it was not possible to resolve the reference to a valid target.
   */
  @Nullable
  PsiElement resolve();

  default boolean isForeignFileImport() {
    return false;
  }
}
