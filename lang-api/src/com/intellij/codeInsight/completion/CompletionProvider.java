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
public abstract class CompletionProvider<V extends CompletionParameters> {

  public abstract void addCompletions(@NotNull V parameters, final ProcessingContext context, @NotNull CompletionResultSet result);

}
