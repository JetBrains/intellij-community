// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.compiler.chainsSearch.completion;

import com.intellij.codeInsight.completion.CompletionLocation;
import com.intellij.codeInsight.completion.CompletionWeigher;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.compiler.chainsSearch.completion.lookup.JavaRelevantChainLookupElement;
import org.jetbrains.annotations.NotNull;

final class MethodChainWeigher extends CompletionWeigher {
  @Override
  public Comparable weigh(@NotNull final LookupElement element, @NotNull final CompletionLocation location) {
    if (element instanceof JavaRelevantChainLookupElement) {
      return ((JavaRelevantChainLookupElement)element).getChainRelevance();
    }
    return null;
  }
}
