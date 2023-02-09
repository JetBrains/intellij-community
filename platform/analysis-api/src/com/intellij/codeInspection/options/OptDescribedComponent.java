// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.options;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.HtmlChunk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Interface for components that come with a contextual description.
 */
public sealed interface OptDescribedComponent permits OptString, OptStringList, OptTable {

  @Nullable
  default OptDescribedComponent description(@NotNull @NlsContexts.Tooltip String description) { return this; }

  /**
   * @return an additional description of the item (may contain simple HTML formatting only, no external images, etc.)
   */
  @Nullable
  default OptDescribedComponent description(@NotNull HtmlChunk description) { return this; }

}
