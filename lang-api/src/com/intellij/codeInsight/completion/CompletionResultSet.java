/*
 * Copyright (c) 2008 Your Corporation. All Rights Reserved.
 */
package com.intellij.codeInsight.completion;

import org.jetbrains.annotations.NotNull;
import com.intellij.util.AsyncConsumer;

/**
 * @author peter
 */
public abstract class CompletionResultSet<Result> {
  private PrefixMatcher myPrefixMatcher;

  protected CompletionResultSet(final PrefixMatcher prefixMatcher) {
    myPrefixMatcher = prefixMatcher;
  }

  public abstract void addElement(@NotNull final Result result);

  public abstract void setSuccessorFilter(AsyncConsumer<Result> consumer);

  public abstract void stopHere();

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
