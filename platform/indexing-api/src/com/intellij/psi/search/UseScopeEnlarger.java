// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.ApiStatus.OverrideOnly;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Extend default {@link PsiElement#getUseScope() use scope} of PSI element.
 * <p>
 * This allows e.g. searching for usages in configuration files outside of regular use scope.
 *
 * @see PsiSearchHelper#getUseScope(PsiElement)
 */
@OverrideOnly
public abstract class UseScopeEnlarger {
  @ApiStatus.Internal
  public static final ExtensionPointName<UseScopeEnlarger> EP_NAME = ExtensionPointName.create("com.intellij.useScopeEnlarger");

  public abstract @Nullable SearchScope getAdditionalUseScope(@NotNull PsiElement element);
}
