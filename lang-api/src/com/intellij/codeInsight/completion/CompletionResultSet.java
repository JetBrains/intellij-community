/*
 * Copyright (c) 2008 Your Corporation. All Rights Reserved.
 */
package com.intellij.codeInsight.completion;

import org.jetbrains.annotations.NotNull;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.util.Consumer;

/**
 * @author peter
 */
public abstract class CompletionResultSet {
  private PrefixMatcher myPrefixMatcher;
  private final Consumer<LookupElement> myConsumer;

  protected CompletionResultSet(final PrefixMatcher prefixMatcher, Consumer<LookupElement> consumer) {
    myPrefixMatcher = prefixMatcher;
    myConsumer = consumer;
  }

  public void addElement(@NotNull final LookupElement result) {
    if (result.isPrefixMatched() || result.setPrefixMatcher(getPrefixMatcher())) {
      myConsumer.consume(result);
    }
  }

  public void setPrefixMatcher(@NotNull PrefixMatcher matcher) {
    myPrefixMatcher = matcher;
  }

  /**
   * Creates a default camel-hump prefix matcher based on given prefix
   * @param prefix
   */
  public abstract void setPrefixMatcher(@NotNull String prefix);

  @NotNull
  public PrefixMatcher getPrefixMatcher() {
    return myPrefixMatcher;
  }

}
