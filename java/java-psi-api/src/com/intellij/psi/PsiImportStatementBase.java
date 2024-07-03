// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.util.ArrayFactory;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a Java {@code import} or {@code import static} statement.
 */
public interface PsiImportStatementBase extends PsiImportDeclaration {
  /**
   * The empty array of PSI base import statements which can be reused to avoid unnecessary allocations.
   */
  PsiImportStatementBase[] EMPTY_ARRAY = new PsiImportStatementBase[0];

  ArrayFactory<PsiImportStatementBase> ARRAY_FACTORY = count -> count == 0 ? EMPTY_ARRAY : new PsiImportStatementBase[count];

  /**
   * Checks if the statement represents a single element or on-demand import.
   *
   * @return true if the import statement is on-demand, false otherwise.
   */
  boolean isOnDemand();

  /**
   * Returns the reference element which specifies the imported class, package or member.
   *
   * @return the import reference element.
   * @see PsiImportStaticReferenceElement
   */
  @Nullable
  PsiJavaCodeReferenceElement getImportReference();
}
