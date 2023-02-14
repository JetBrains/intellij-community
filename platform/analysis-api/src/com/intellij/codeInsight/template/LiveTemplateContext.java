// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Common interface for registered {@link LiveTemplateContextBean} extensions and instances provided by {@link LiveTemplateContextProvider}.
 */
public interface LiveTemplateContext {
  @NotNull String getContextId();

  @Nullable String getBaseContextId();

  @NotNull TemplateContextType getTemplateContextType();
}
