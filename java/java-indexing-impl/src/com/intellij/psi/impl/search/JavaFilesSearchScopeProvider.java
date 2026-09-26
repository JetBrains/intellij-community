// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.search;

import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

/**
 * Provider for a global scope that contains Java files only
 */
public interface JavaFilesSearchScopeProvider {
  /**
   * @return scope that contains Java files only
   */
  @NotNull GlobalSearchScope getScope();
}
