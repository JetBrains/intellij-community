/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.Weigher;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 * @see CompletionContributor
 * @see PrioritizedLookupElement
 */
public abstract class CompletionWeigher extends Weigher<LookupElement, CompletionLocation> {

  @Override
  public abstract Comparable weigh(@NotNull final LookupElement element, @NotNull final CompletionLocation location);
}
