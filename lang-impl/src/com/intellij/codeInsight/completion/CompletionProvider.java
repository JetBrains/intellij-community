/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public abstract class CompletionProvider<T, V extends CompletionParameters> {
  public static final CompletionProvider EMPTY_PROVIDER = new CompletionProvider() {
    public void addCompletions(@NotNull final CompletionParameters parameters,
                               final ProcessingContext context, @NotNull final CompletionResultSet result) {
    }
  };

  public abstract void addCompletions(@NotNull V parameters, final ProcessingContext context, @NotNull CompletionResultSet<T> result);

}
