/*
 * Copyright (c) 2008 Your Corporation. All Rights Reserved.
 */
package com.intellij.codeInsight.completion;

import com.intellij.util.QueryResultSet;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public interface CompletionResultSet<Result> extends QueryResultSet<Result>{

  void setPrefixMatcher(@NotNull PrefixMatcher matcher);

  /**
   * Creates a default camel-hump prefix matcher based on given prefix
   * @param prefix
   */
  void setPrefixMatcher(@NotNull String prefix);

  @NotNull PrefixMatcher getPrefixMatcher();

}
