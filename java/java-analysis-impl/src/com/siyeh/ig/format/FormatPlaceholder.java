// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.format;

import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a single placeholder in a format string, like %d in printf-format, or {1} in MessageFormat-format
 */
public interface FormatPlaceholder {
  /**
   * @return zero-based index of the argument, which corresponds to this format placeholder 
   */
  int index();

  /**
   * @return range inside the original format string which is occupied by a given placeholder
   */
  @NotNull TextRange range();
}
