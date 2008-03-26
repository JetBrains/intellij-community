/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Controls the text to display at the bottom of lookup list
 *
 * @author peter
 */
public abstract class CompletionAdvertiser {

  @Nullable public abstract String advertise(@NotNull CompletionParameters parameters, final ProcessingContext context);

  @Nullable public abstract String handleEmptyLookup(@NotNull CompletionParameters parameters, final ProcessingContext context);

}