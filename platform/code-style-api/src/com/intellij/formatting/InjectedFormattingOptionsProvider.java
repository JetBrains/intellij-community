// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides information about formatting of injected PSI files
 */
public interface InjectedFormattingOptionsProvider {
  ExtensionPointName<InjectedFormattingOptionsProvider> EP_NAME = ExtensionPointName.create("com.intellij.formatting.injectedOptions");

  /**
   * For a given PSI file, returns
   * - `true` if code formatting should be delegated to a file which contains this injected file (top-level file)
   * - `false` if code formatting shouldn't be delegated to a top-level file
   * - `null` if default behavior should be chosen, or it should be delegated to another provider
   * @see InjectedFormattingOptionsService
   */
  @Nullable Boolean shouldDelegateToTopLevel(@NotNull PsiFile file);
}
