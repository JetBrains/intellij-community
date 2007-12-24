/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public abstract class CompletionProvider<T, V extends CompletionParameters<T>> {
  public abstract void addCompletions(@NotNull CompletionEnvironment environment, @NotNull CompletionQuery<T,V> result);
}
