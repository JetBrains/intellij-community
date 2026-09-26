// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.navigation;

import com.intellij.model.Symbol;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @see com.intellij.model.psi.ImplicitReferenceProvider
 */
@ApiStatus.Experimental
public interface DirectNavigationProvider {

  ExtensionPointName<DirectNavigationProvider> EP_NAME = ExtensionPointName.create("com.intellij.lang.directNavigationProvider");

  /**
   * Implement this method to support simple navigation without supporting {@link Symbol}-based actions,
   * e.g. navigation from a 'break' statement to the end of a 'for' loop in Java.<br/>
   * Returning non-null value from this method will enable navigation and link highlighting on hover.
   */
  @Nullable
  PsiElement getNavigationElement(@NotNull PsiElement element);
}
