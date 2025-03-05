// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.safeDelete;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

/**
 * @author Max Medvedev
 */
@ApiStatus.Internal
public abstract class ImportSearcher {
  private static final ExtensionPointName<ImportSearcher> EP_NAME = ExtensionPointName.create("com.intellij.safeDelete.importSearcher");

  /**
   * @return found import or null
   */
  @Contract(pure = true)
  public abstract @Nullable PsiElement findImport(PsiElement element, boolean onlyNonStatic);

  public static @Nullable PsiElement getImport(PsiElement element, boolean onlyNonStatic) {
    for (ImportSearcher searcher : EP_NAME.getExtensions()) {
      PsiElement anImport = searcher.findImport(element, onlyNonStatic);
      if (anImport != null) return anImport;
    }

    return null;
  }
}
