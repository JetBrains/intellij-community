// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import org.jetbrains.annotations.Nullable;

/**
 * Represents a Java {@code import static} statement.
 */
public interface PsiImportStaticStatement extends PsiImportStatementBase {
  /**
   * The empty array of PSI static import statements which can be reused to avoid unnecessary allocations.
   */
  PsiImportStaticStatement[] EMPTY_ARRAY = new PsiImportStaticStatement[0];

  /**
   * Resolves the reference to the class from which members are imported.
   *
   * @return the class from which members are imported, or null if the reference resolve failed
   * or the resolve target is not a class.
   */
  @Nullable PsiClass resolveTargetClass();

  /**
   * Returns the name of the member imported from the statement.
   *
   * @return the name of the member, or null for an on-demand import.
   */
  @Nullable String getReferenceName();
}
