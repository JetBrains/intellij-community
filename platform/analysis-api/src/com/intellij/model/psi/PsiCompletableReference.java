// Copyright 2000-2025 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.psi;

import com.intellij.codeInsight.lookup.LookupElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;

/**
 * Implement this interface to support reference-based completion.
 */
public interface PsiCompletableReference extends PsiSymbolReference {

  /**
   * @return the collection {@link LookupElement} instances representing all identifiers that are visible at the location of the reference.
   * The items are used to build the lookup list for basic code completion.
   * The list of visible identifiers may not be filtered by the completion prefix string, the filtering is performed later by the IDE.
   */
  @NotNull @Unmodifiable
  Collection<@NotNull LookupElement> getCompletionVariants();
}
