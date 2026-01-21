// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.util.ArrayFactory;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a Java {@code import} or {@code import static} statement.
 */
public interface PsiImportStatementBase extends PsiElement {
  /**
   * The empty array of PSI base import statements which can be reused to avoid unnecessary allocations.
   */
  PsiImportStatementBase[] EMPTY_ARRAY = new PsiImportStatementBase[0];

  ArrayFactory<PsiImportStatementBase> ARRAY_FACTORY = count -> count == 0 ? EMPTY_ARRAY : new PsiImportStatementBase[count];

  /**
   * Checks if the statement represents a single element or a group import (on-demand).
   *
   * @return true if the import statement is on-demand or module, false otherwise.
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

  /**
   * Resolves the reference to the imported class, package, module or member.
   *
   * @return the target element, or null if it was not possible to resolve the reference to a valid target.
   */
  @Nullable
  PsiElement resolve();

  boolean isForeignFileImport();

  
  default boolean isReplaceEquivalent(PsiImportStatementBase other) {
    return false;
  }
}
