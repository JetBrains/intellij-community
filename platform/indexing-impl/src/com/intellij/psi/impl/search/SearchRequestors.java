// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.search;

import com.intellij.model.SymbolReference;
import com.intellij.model.search.SearchRequestor;
import com.intellij.model.search.SearchSymbolReferenceParameters;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.util.Processor;
import com.intellij.util.Query;
import org.jetbrains.annotations.NotNull;

final class SearchRequestors {

  private static final ExtensionPointName<SearchRequestor> EP_NAME = ExtensionPointName.create("com.intellij.searchRequestor");

  private SearchRequestors() {}

  static void collectSearchRequests(@NotNull SearchSymbolReferenceParameters parameters,
                                    @NotNull Processor<? super Query<? extends SymbolReference>> querySink) {
    for (SearchRequestor requestor : EP_NAME.getExtensions()) {
      ProgressManager.checkCanceled();
      requestor.collectSearchRequests(parameters).forEach(querySink::process);
    }
  }
}
