// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.search;

import com.intellij.model.search.ModelReferenceSearchParameters;
import com.intellij.model.search.SearchRequestCollector;
import com.intellij.model.search.SearchRequestor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.progress.ProgressManager;
import org.jetbrains.annotations.NotNull;

final class SearchRequestors {

  private static final ExtensionPointName<SearchRequestor> EP_NAME = ExtensionPointName.create("com.intellij.searchRequestor");

  private SearchRequestors() {}

  static void collectSearchRequests(@NotNull SearchRequestCollector session, @NotNull ModelReferenceSearchParameters parameters) {
    for (SearchRequestor requestor : EP_NAME.getExtensions()) {
      ProgressManager.checkCanceled();
      requestor.collectSearchRequests(session, parameters);
    }
  }
}
