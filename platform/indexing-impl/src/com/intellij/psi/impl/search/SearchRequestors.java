// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.search;

import com.intellij.model.search.SearchParameters;
import com.intellij.model.search.SearchRequestor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.ClassExtension;
import com.intellij.util.Query;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

final class SearchRequestors {

  private static final ClassExtension<SearchRequestor<?, ?>> EXTENSION = new ClassExtension<>("com.intellij.searchRequestor");

  private SearchRequestors() {}

  static <R> void collectSearchRequests(@NotNull SearchParameters<R> parameters, @NotNull Consumer<? super Query<? extends R>> querySink) {
    for (SearchRequestor<?, ?> requestor : EXTENSION.forKey(parameters.getClass())) {
      ProgressManager.checkCanceled();
      //noinspection unchecked
      SearchRequestor<SearchParameters<R>, R> _requestor = (SearchRequestor<SearchParameters<R>, R>)requestor;
      _requestor.collectSearchRequests(parameters).forEach(querySink);
    }
  }
}
