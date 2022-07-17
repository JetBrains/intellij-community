// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * The extension helps to provide a custom processing logic for overridden methods.
 * For example, Kotlin has a certain <code>override</code> modifier, while Java has annotation {@link Override}.
 */
@ApiStatus.Internal
public interface OverrideMethodsProcessor {
  ExtensionPointName<OverrideMethodsProcessor> EP_NAME = ExtensionPointName.create("com.intellij.refactoring.overrideMethodProcessor");

  /**
   * Should check if {@code element} has override attribute and iff, then remove it.
   * @return true if attribute was found so further processing is not required
   * <p> The method should be called under write action.
   */
  boolean removeOverrideAttribute(@NotNull PsiElement element);
}
