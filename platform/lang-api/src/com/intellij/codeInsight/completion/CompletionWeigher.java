/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.Weigher;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public abstract class CompletionWeigher extends Weigher<LookupElement, CompletionLocation> {

  public abstract Comparable weigh(@NotNull final LookupElement element, final CompletionLocation location);
}
