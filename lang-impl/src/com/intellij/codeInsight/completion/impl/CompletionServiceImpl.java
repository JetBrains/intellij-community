/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion.impl;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.PsiElement;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * @author peter
 */
public class CompletionServiceImpl extends CompletionService{
  private Throwable myTrace = null;
  private CompletionProgressIndicator myCurrentCompletion;

  public static CompletionServiceImpl getCompletionService() {
    return (CompletionServiceImpl)CompletionService.getCompletionService();
  }

  @Override
  public String getAdvertisementText() {
    final CompletionProgressIndicator completion = getCompletionService().getCurrentCompletion();
    return completion == null ? null : completion.getLookup().getAdvertisementText();
  }

  public void setAdvertisementText(@Nullable final String text) {
    final CompletionProgressIndicator completion = getCompletionService().getCurrentCompletion();
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

  @Override
  public CompletionProgressIndicator getCurrentCompletion() {
    return myCurrentCompletion;
  }

  public void setCurrentCompletion(@Nullable final CompletionProgressIndicator indicator) {
    if (indicator != null) {
      final CompletionProgressIndicator oldCompletion = myCurrentCompletion;
      final Throwable oldTrace = myTrace;
      myCurrentCompletion = indicator;
      myTrace = new Throwable();
      if (oldCompletion != null) {
        final StringWriter writer = new StringWriter();
        oldTrace.printStackTrace(new PrintWriter(writer));
        throw new RuntimeException("SHe's not dead yet!\nthis=" + indicator + "\ncurrent=" + oldCompletion + "\ntrace=" + writer.toString());
      }
    } else {
      myCurrentCompletion = null;
    }
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
