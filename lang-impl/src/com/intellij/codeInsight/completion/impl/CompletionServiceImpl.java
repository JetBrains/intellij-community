/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion.impl;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.util.Consumer;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class CompletionServiceImpl extends CompletionService{
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
