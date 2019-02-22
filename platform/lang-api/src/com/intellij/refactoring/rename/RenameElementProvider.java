// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.rename;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Helps refactoring actions to fetch element from data context
 */
@FunctionalInterface
public interface RenameElementProvider {
  ExtensionPointName<RenameElementProvider> EP_NAME = new ExtensionPointName<>("com.intellij.renameElementProvider");

  /**
   * Called from update method of refactoring action and must be fast!
   * Check that context has correct language type, otherwise return null
   */
  @Nullable
  PsiElement getElement(@NotNull DataContext dataContext);
}
