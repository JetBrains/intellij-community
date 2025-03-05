// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.chainsSearch.completion.lookup;

import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementDecorator;
import com.intellij.compiler.chainsSearch.ChainRelevance;
import org.jetbrains.annotations.NotNull;

public final class JavaRelevantChainLookupElement extends LookupElementDecorator<LookupElement> {
  private final ChainRelevance myChainRelevance;

  public JavaRelevantChainLookupElement(final @NotNull LookupElement delegate, final @NotNull ChainRelevance relevance) {
    super(delegate);
    myChainRelevance = relevance;
  }

  public @NotNull ChainRelevance getChainRelevance() {
    return myChainRelevance;
  }

  @Override
  public AutoCompletionPolicy getAutoCompletionPolicy() {
    return AutoCompletionPolicy.NEVER_AUTOCOMPLETE;
  }
}
