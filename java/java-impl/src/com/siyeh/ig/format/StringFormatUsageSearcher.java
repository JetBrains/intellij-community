// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.format;

import com.intellij.find.usages.api.*;
import com.intellij.model.Symbol;
import com.intellij.model.psi.PsiSymbolReference;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.List;

public final class StringFormatUsageSearcher implements UsageSearcher {
  @Override
  public @Unmodifiable @NotNull Collection<? extends Usage> collectImmediateResults(@NotNull UsageSearchParameters parameters) {
    SearchTarget target = parameters.getTarget();
    if (target instanceof StringFormatSymbolReferenceProvider.JavaFormatArgumentSymbol symbol) {
      PsiExpression expression = symbol.getFormatString();
      if (expression == null) return List.of();
      PsiExpression arg = symbol.getExpression();
      return getFormatUsages(symbol, expression, arg, StringFormatSymbolReferenceProvider::getReferences);
    }
    return List.of();
  }

  private static @Unmodifiable @NotNull List<Usage> getFormatUsages(@NotNull Symbol symbol,
                                                                    @NotNull PsiExpression expression,
                                                                    @NotNull PsiExpression arg,
                                                                    @NotNull Function<? super PsiLiteralExpression, ? extends Iterable<? extends PsiSymbolReference>> function) {
    return SyntaxTraverser.psiTraverser(expression)
      .traverse()
      .filter(PsiLiteralExpression.class)
      .flatMap(function)
      .filter(ref -> ref.resolvesTo(symbol))
      .<Usage>map(PsiUsage::textUsage)
      .append(List.of(new PsiElementUsage(arg)))
      .toList();
  }
}
