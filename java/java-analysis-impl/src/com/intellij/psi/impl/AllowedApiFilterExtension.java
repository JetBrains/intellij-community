// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.psi.impl;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * Allows disabling conversions when conversion target is not available in the current platform e.g., GWT.
 * 
 * Currently, is used by `Convert to AtomicReference/ThreadLocal` intentions.
 */
public abstract class AllowedApiFilterExtension {
  public static final ExtensionPointName<AllowedApiFilterExtension> EP_NAME = ExtensionPointName.create("com.intellij.allowedApiFilter");

  @Contract(pure = true)
  public abstract boolean isClassForbidden(@NotNull String fqn, @NotNull PsiElement place);

  public static boolean isClassAllowed(@NotNull String fqn, @NotNull PsiElement place) {
    for (AllowedApiFilterExtension extension : EP_NAME.getExtensionList()) {
      if (extension.isClassForbidden(fqn, place)) return false;
    }
    return true;
  }
}
