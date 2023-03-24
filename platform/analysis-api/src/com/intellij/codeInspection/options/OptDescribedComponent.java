// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.options;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.HtmlChunk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Interface for components that come with a contextual description.
 */
public sealed interface OptDescribedComponent permits OptCheckbox, OptExpandableString, OptNumber, OptString, OptStringList, OptTable {

  /**
   * @param description textual description
   * @return an equivalent component but with a description
   * @throws IllegalStateException if description was already set
   */
  @Nullable
  OptRegularComponent description(@NotNull @NlsContexts.Tooltip String description);

  /**
   * @return the same component with an additional description
   */
  @Nullable
  OptRegularComponent description(@NotNull HtmlChunk description);

  /**
   * @return an additional description of the item (may contain simple HTML formatting only, no external images, etc.)
   */
  @Nullable
  HtmlChunk description();

}
