/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.search.searches;

import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.QueryExecutor;
import org.jetbrains.annotations.NotNull;

/**
 * A query executor that handles all searches within the specified scope, requesting the default query
 * executor to not perform any searches in this scope.
 *
 * @author yole
 */
public interface ScopedQueryExecutor<Result, Param> extends QueryExecutor<Result, Param> {
  /**
   * Returns the scope handled by this executor.
   */
  @NotNull
  GlobalSearchScope getScope(Param param);
}
