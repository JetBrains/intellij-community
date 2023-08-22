// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

public interface OptionsContainingConfigurable {
  default @NotNull Set<String> processListOptions() {
    return Collections.emptySet();
  }

  /**
   * @return A map of paths each having a set of options which belong to it, for e.g. a tab name and options under
   *         the tab.
   */
  default @NotNull Map<String,Set<String>> processListOptionsWithPaths() {
    return Collections.emptyMap();
  }
}
