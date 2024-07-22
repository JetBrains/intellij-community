// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Represents an element implicitly imported in a java file
 * (It doesn't include implicitly imported packages (see {@link PsiJavaFile#getImplicitlyImportedPackages()}))
 */
@ApiStatus.Experimental
public interface ImplicitlyImportedElement {
  @NotNull ImplicitlyImportedElement @NotNull [] EMPTY_ARRAY = new ImplicitlyImportedElement[0];

  /**
   * @return a new or cached instance of {@code PsiImportStatementBase} representing an implicitly imported element.
   */
  @NotNull
  PsiImportStatementBase createImportStatement();
}
