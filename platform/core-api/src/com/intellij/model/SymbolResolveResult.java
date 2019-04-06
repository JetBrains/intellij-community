// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model;

import org.jetbrains.annotations.ApiStatus.Experimental;
import org.jetbrains.annotations.NotNull;

/**
 * A result of resolving a {@link SymbolReference}.
 */
@Experimental
public interface SymbolResolveResult {

  /**
   * @return referenced Symbol
   */
  @NotNull
  Symbol getTarget();
}
