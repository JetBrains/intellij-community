// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;

/**
 * Extension point to control which {@link LookupElement}s should be skipped during code completion.
 * <p/>
 * Register via extension point "com.intellij.completion.skip".
 */
public abstract class CompletionPreselectSkipper {
  public static final ExtensionPointName<CompletionPreselectSkipper> EP_NAME = ExtensionPointName.create("com.intellij.completion.skip");

  public abstract boolean skipElement(@NotNull LookupElement element, @NotNull CompletionLocation location);
}
