// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search;

import com.intellij.util.Query;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Example:
 * When searching for references to some getter method 'getFoo()',
 * we also want to include property references 'foo' from some XMLs.
 * In this case we order to pass all references with 'foo' text that resolve to 'getFoo()' into original processor.
 * <p/>
 * Implementations should be registered at {@code com.intellij.searcher} extension point.
 * <p/>
 * It's highly advised to use {@link SearchService} methods to build additional queries.
 */
public interface Searcher<P extends SearchParameters<R>, R> {

  /**
   * @return read-only collection of queries to be executed when the search is run with {@code parameters}
   */
  @NotNull
  Collection<? extends Query<? extends R>> collectSearchRequests(@NotNull P parameters);
}
