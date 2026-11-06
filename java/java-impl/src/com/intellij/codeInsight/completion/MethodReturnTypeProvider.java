// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.PsiTypeLookupItem;
import com.intellij.java.completion.modcommand.MethodReturnTypeItemProvider;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;

final class MethodReturnTypeProvider {
  static final ElementPattern<PsiElement> IN_METHOD_RETURN_TYPE = MethodReturnTypeItemProvider.IN_METHOD_RETURN_TYPE;

  static void addProbableReturnTypes(@NotNull PsiElement position, Consumer<? super LookupElement> consumer) {
    MethodReturnTypeItemProvider.processProbableReturnTypes(position, (type, priority) -> consumer.consume(
      PrioritizedLookupElement.withPriority(PsiTypeLookupItem.createLookupItem(type, position), priority)));
  }
}
