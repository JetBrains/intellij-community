// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang;

import org.jetbrains.annotations.NotNull;

/**
 * Provides a singleton {@link MetaLanguage} instance for the {@code com.intellij.metaLanguage} extension point.
 * Implement this interface and register the provider instead of extending {@link MetaLanguage} directly.
 */
public interface MetaLanguageProvider {

  @NotNull MetaLanguage getLanguage();
}
