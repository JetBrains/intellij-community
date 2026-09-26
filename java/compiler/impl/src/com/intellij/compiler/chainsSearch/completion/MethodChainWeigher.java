// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.chainsSearch.completion;

import com.intellij.codeInsight.completion.CompletionLocation;
import com.intellij.codeInsight.completion.CompletionWeigher;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.compiler.chainsSearch.completion.lookup.JavaRelevantChainLookupElement;
import org.jetbrains.annotations.NotNull;

final class MethodChainWeigher extends CompletionWeigher {
  @Override
  public Comparable weigh(final @NotNull LookupElement element, final @NotNull CompletionLocation location) {
    if (element instanceof JavaRelevantChainLookupElement) {
      return ((JavaRelevantChainLookupElement)element).getChainRelevance();
    }
    return null;
  }
}
