// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.psi;

import com.intellij.model.Symbol;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @see com.intellij.navigation.DirectNavigationProvider
 */
public interface ImplicitReferenceProvider {

  @Internal
  ExtensionPointName<ImplicitReferenceProvider> EP_NAME = ExtensionPointName.create("com.intellij.psi.implicitReferenceProvider");

  /**
   * Implement this method to support {@link Symbol}-based actions.
   * <p/>
   * If this method returns non-empty collection then the element is treated as a reference,
   * enabling various actions accessible on a referenced Symbol,
   * for example, navigation and link highlighting on hover.
   * Such "reference" won't be found (or renamed, etc).
   * <p/>
   * This method is called for each element in the PSI tree
   * starting from the leaf element at the caret offset up to the file.
   */
  @NotNull
  Collection<? extends Symbol> resolveAsReference(@NotNull PsiElement element);
}
