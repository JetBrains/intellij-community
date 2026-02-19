// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.Weigher;
import org.jetbrains.annotations.NotNull;

/**
 * @see CompletionContributor
 * @see PrioritizedLookupElement
 */
public abstract class CompletionWeigher extends Weigher<LookupElement, CompletionLocation> {

  @Override
  public abstract Comparable weigh(final @NotNull LookupElement element, final @NotNull CompletionLocation location);
}
