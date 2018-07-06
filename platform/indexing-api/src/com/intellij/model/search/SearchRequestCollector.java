// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search;

import com.intellij.model.Symbol;
import com.intellij.model.SymbolReference;
import com.intellij.util.Preprocessor;
import com.intellij.util.Query;
import org.jetbrains.annotations.NotNull;

public interface SearchRequestCollector {

  /**
   * @return parameters with which search was started
   */
  @NotNull
  SymbolReferenceSearchParameters getParameters();

  /**
   * Orders to pass sub query results into result processor as is. <br/>
   * This operation is equivalent to:
   * <pre>
   * Query&lt;SymbolReference> query search(params);
   * query.forEach(originalProcessor);
   * </pre>
   */
  void searchSubQuery(@NotNull Query<? extends SymbolReference> subQuery);

  /**
   * Orders to pass sub query results into result processor after preprocessing.<br/>
   * This operation is equivalent to:
   * <pre>
   * Query&lt;SomeResult> query = search(someParams);
   * query.forEach(new Processor&lt;SomeResult> () {
   *   boolean process(SomeResult r) {
   *     SymbolReference ref = mapSomeResultToReference(r);
   *     return originalProcessor.process(ref)
   *   }
   * })
   * </pre>
   * Note that {@code mapSomeResultToReference} is arbitrary as well as calling {@code originalProcessor},
   * i.e. returned processor is free to do anything.
   */
  <T> void searchSubQuery(@NotNull Query<T> subQuery, @NotNull Preprocessor<SymbolReference, T> preprocessor);

  /**
   * Starts sub search forming by creating builder-like object with handy methods
   */
  @NotNull
  SearchTargetRequestor searchTarget(@NotNull Symbol target);

  /**
   * Starts sub search forming by creating builder-like object with handy methods
   */
  @NotNull
  SearchWordRequestor searchWord(@NotNull String word);
}
