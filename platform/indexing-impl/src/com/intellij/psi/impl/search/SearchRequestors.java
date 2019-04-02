// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.search;

import com.intellij.model.SymbolReference;
import com.intellij.model.search.SearchRequestCollector;
import com.intellij.model.search.SearchRequestor;
import com.intellij.model.search.SearchRequestor2;
import com.intellij.model.search.SymbolReferenceSearchParameters;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.util.Processor;
import com.intellij.util.Query;
import org.jetbrains.annotations.NotNull;

final class SearchRequestors {

  private static final ExtensionPointName<SearchRequestor> EP_NAME = ExtensionPointName.create("com.intellij.searchRequestor");
  private static final ExtensionPointName<SearchRequestor2> EP_NAME2 = ExtensionPointName.create("com.intellij.searchRequestor2");

  private SearchRequestors() {}

  static void collectSearchRequests(@NotNull SearchRequestCollector collector) {
    for (SearchRequestor requestor : EP_NAME.getExtensions()) {
      ProgressManager.checkCanceled();
      requestor.collectSearchRequests(collector);
    }
  }

  static void collectSearchRequests(@NotNull SymbolReferenceSearchParameters parameters,
                                    @NotNull Processor<? super Query<? extends SymbolReference>> querySink) {

    for (SearchRequestor2 requestor : EP_NAME2.getExtensions()) {
      ProgressManager.checkCanceled();
      requestor.collectSearchRequests(parameters).forEach(querySink::process);
    }
  }




}
