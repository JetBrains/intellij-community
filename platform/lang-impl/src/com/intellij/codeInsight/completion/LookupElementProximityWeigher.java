// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.util.NullableComputable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.proximity.PsiProximityComparator;
import org.jetbrains.annotations.NotNull;

public final class LookupElementProximityWeigher extends CompletionWeigher {

  @Override
  public Comparable weigh(final @NotNull LookupElement item, final @NotNull CompletionLocation location) {
    // don't extract variable from getPsiElement to avoid excessive memory usage
    if (item.getPsiElement() != null) {
      return PsiProximityComparator.getProximity((NullableComputable<PsiElement>)() -> item.getPsiElement(), location.getCompletionParameters().getPosition(), location.getProcessingContext());
    }
    return null;
  }
}
