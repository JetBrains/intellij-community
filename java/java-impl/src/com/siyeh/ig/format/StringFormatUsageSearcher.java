// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.format;

import com.intellij.find.usages.api.*;
import com.intellij.lang.logging.resolve.JvmLoggerArgumentSymbol;
import com.intellij.lang.logging.resolve.JvmLoggerFormatSymbolReferenceProviderKt;
import com.intellij.model.Pointer;
import com.intellij.model.Symbol;
import com.intellij.model.psi.PsiSymbolReference;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

public final class StringFormatUsageSearcher implements UsageSearcher {
  @NotNull
  @Override
  public Collection<? extends Usage> collectImmediateResults(@NotNull UsageSearchParameters parameters) {
    SearchTarget target = parameters.getTarget();
    if (target instanceof StringFormatSymbolReferenceProvider.JavaFormatArgumentSymbol symbol) {
      PsiExpression expression = symbol.getFormatString();
      if (expression == null) return List.of();
      PsiExpression arg = symbol.getExpression();
      return getFormatUsages(symbol, expression, arg, StringFormatSymbolReferenceProvider::getReferences);
    }
    else if (target instanceof JvmLoggerArgumentSymbol symbol) {
      PsiExpression expression = symbol.getFormatString();
      if (expression == null) return List.of();
      PsiExpression arg = symbol.getExpression();
      return getFormatUsages(symbol, expression, arg, JvmLoggerFormatSymbolReferenceProviderKt::getLogArgumentReferences);
    }
    return List.of();
  }

  @NotNull
  private static List<Usage> getFormatUsages(Symbol symbol,
                                             PsiExpression expression,
                                             PsiExpression arg,
                                             @NotNull Function<? super PsiLiteralExpression, ? extends Iterable<? extends PsiSymbolReference>> function) {
    return SyntaxTraverser.psiTraverser(expression)
      .traverse()
      .filter(PsiLiteralExpression.class)
      .flatMap(function)
      .filter(ref -> ref.resolvesTo(symbol))
      .<Usage>map(PsiUsage::textUsage)
      .append(List.of(new DefUsage(arg)))
      .toList();
  }


  private static class DefUsage implements PsiUsage {
    private final @NotNull PsiExpression myArg;

    private DefUsage(@NotNull PsiExpression arg) {
      myArg = arg;
    }

    @NotNull
    @Override
    public Pointer<? extends PsiUsage> createPointer() {
      return Pointer.hardPointer(this);
    }

    @NotNull
    @Override
    public PsiFile getFile() {
      return myArg.getContainingFile();
    }

    @NotNull
    @Override
    public TextRange getRange() {
      return myArg.getTextRange();
    }

    @Override
    public boolean getDeclaration() {
      return true;
    }
  }
}
