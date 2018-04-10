// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search;

import org.jetbrains.annotations.NotNull;

/**
 * Enables to search something else.
 * <p/>
 * Example:
 * When searching for references to some getter method 'getFoo()',
 * we also want to include property references 'foo' from some XMLs.
 * In this case we order to pass all references with 'foo' text that resolve to 'getFoo()' into original processor.
 * <p/>
 * Implementations should be registered at {@code com.intellij.searchRequestor} extension point.
 */
public interface SearchRequestor {

  void collectSearchRequests(@NotNull SearchRequestCollector collector);
}
