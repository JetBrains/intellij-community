/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.codeInsight.completion.simple;

import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.lookup.LookupItem;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class SimpleLookupItem<T> extends LookupItem<T> {

  public SimpleLookupItem(final T o, @NotNull @NonNls final String lookupString) {
    super(o, lookupString);
  }

  @NotNull
  public LookupItem<T> setTailType(@NotNull final TailType type) {
    super.setTailType(type);
    if (getInsertHandler() == null) {
      setInsertHandler(SimpleInsertHandler.EMPTY_HANDLER);
    }
    return this;
  }

  public LookupItem<T> setCompletionCharHandler(@NotNull final CompletionCharHandler<T> completionCharHandler) {
    super.setCompletionCharHandler(completionCharHandler);
    if (getInsertHandler() == null) {
      setInsertHandler(SimpleInsertHandler.EMPTY_HANDLER);
    }
    return this;
  }
}
