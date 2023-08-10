// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.search.searches;

import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.QueryExecutor;
import org.jetbrains.annotations.NotNull;

/**
 * A query executor that handles all searches within the specified scope, requesting the default query
 * executor to not perform any searches in this scope.
 */
public interface ScopedQueryExecutor<Result, Param> extends QueryExecutor<Result, Param> {
  /**
   * Returns the scope handled by this executor.
   */
  @NotNull
  GlobalSearchScope getScope(Param param);
}
