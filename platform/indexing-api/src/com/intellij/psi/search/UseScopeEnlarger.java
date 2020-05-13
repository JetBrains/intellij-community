// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.search;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Extend default {@link PsiElement#getUseScope() use scope} of PSI element.
 * <p>
 * This allows e.g. searching for usages in configuration files outside of regular use scope.
 *
 * @see PsiSearchHelper#getUseScope(PsiElement)
 */
public abstract class UseScopeEnlarger {
  @ApiStatus.Internal
  public static final ExtensionPointName<UseScopeEnlarger> EP_NAME = ExtensionPointName.create("com.intellij.useScopeEnlarger");

  @Nullable
  public abstract SearchScope getAdditionalUseScope(@NotNull PsiElement element);
}
