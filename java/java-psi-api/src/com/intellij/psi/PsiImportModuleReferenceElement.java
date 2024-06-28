// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import org.jetbrains.annotations.Nullable;

/**
 * Represents a reference to the member imported by a Java {@code import module}
 * statement.
 */
public interface PsiImportModuleReferenceElement extends PsiJavaCodeReferenceElement {
  /**
   * Returns the reference element specifying the module from which the member is imported.
   *
   * @return the reference element specifying the module.
   */
  @Nullable PsiJavaModuleReferenceElement getModuleReference();
}
