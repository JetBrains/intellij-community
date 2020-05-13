// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search;

import com.intellij.openapi.project.Project;
import com.intellij.util.Query;
import org.jetbrains.annotations.NotNull;

/**
 * Base interface for search parameters.
 *
 * @param <R> type of search result, it is used to bind type of search parameters to the type of {@link Query}
 */
public interface SearchParameters<R> {

  @NotNull
  Project getProject();

  boolean areValid();
}
