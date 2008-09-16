/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion.impl;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class CompletionServiceImpl extends CompletionService{
  @Override
  public boolean isAdvertisementTextSet() {
    final CompletionProgressIndicator completion = CompletionProgressIndicator.getCurrentCompletion();
    return completion == null || StringUtil.isNotEmpty(completion.getLookup().getAdvertisementText());
  }

  public void setAdvertisementText(@Nullable final String text) {
    final CompletionProgressIndicator completion = CompletionProgressIndicator.getCurrentCompletion();
    if (completion != null) {
      completion.getLookup().setAdvertisementText(text);
    }
  }

  public CompletionResultSet createResultSet(final CompletionParameters parameters, final Consumer<LookupElement> consumer) {
    final PsiElement position = parameters.getPosition();
    final String prefix = CompletionData.findPrefixStatic(position, parameters.getOffset());

    final String textBeforePosition = parameters.getOriginalFile().getText().substring(0, parameters.getOffset());

    return new CompletionResultSetImpl(consumer, textBeforePosition, new CamelHumpMatcher(prefix));
  }

  private static class CompletionResultSetImpl extends CompletionResultSet {
    private final String myTextBeforePosition;

    public CompletionResultSetImpl(final Consumer<LookupElement> consumer, final String textBeforePosition,
                                   final PrefixMatcher prefixMatcher) {
      super(prefixMatcher, consumer);
      myTextBeforePosition = textBeforePosition;
    }

    @NotNull
    public CompletionResultSet withPrefixMatcher(@NotNull final PrefixMatcher matcher) {
      assert myTextBeforePosition.endsWith(matcher.getPrefix()) : "prefix should be some actual file string just before caret: " + matcher.getPrefix();
      return new CompletionResultSetImpl(getConsumer(), myTextBeforePosition, matcher);
    }

    @NotNull
    public CompletionResultSet withPrefixMatcher(@NotNull final String prefix) {
      return withPrefixMatcher(new CamelHumpMatcher(prefix));
    }
  }
}
