// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.search;

import com.intellij.psi.search.TextOccurenceProcessor;

import java.util.List;
import java.util.Map;

final class WordRequests {

  final Map<SearchWordRequest, List<TextOccurenceProcessor>> immediateWordRequests;
  final Map<SearchWordRequest, List<TextOccurenceProcessorProvider>> deferredWordRequests;

  WordRequests(Map<SearchWordRequest, List<TextOccurenceProcessor>> immediateRequests,
               Map<SearchWordRequest, List<TextOccurenceProcessorProvider>> deferredRequests) {
    immediateWordRequests = immediateRequests;
    deferredWordRequests = deferredRequests;
  }

  boolean isEmpty() {
    return immediateWordRequests.isEmpty() && deferredWordRequests.isEmpty();
  }
}
