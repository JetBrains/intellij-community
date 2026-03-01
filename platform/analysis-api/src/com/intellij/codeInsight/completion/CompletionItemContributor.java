// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.modcompletion.ModCompletionItem;
import com.intellij.modcompletion.ModCompletionItemProvider;
import com.intellij.modcompletion.ModCompletionResult;
import com.intellij.openapi.diagnostic.ReportingClassSubstitutor;
import org.jetbrains.annotations.NotNull;

/**
 * A wrapper for {@link ModCompletionItemProvider} to be used as a {@link CompletionContributor}.
 */
final class CompletionItemContributor extends CompletionContributor implements ReportingClassSubstitutor {
  private final ModCompletionItemProvider myProvider;

  @SuppressWarnings("NonDefaultConstructor")
  CompletionItemContributor(ModCompletionItemProvider provider) {
    myProvider = provider;
  }
  
  @Override
  public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
    ModCompletionItemProvider.CompletionContext context = new ModCompletionItemProvider.CompletionContext(
      parameters.getOriginalFile(), parameters.getOffset(), parameters.getOriginalPosition(), parameters.getPosition(), 
      result.getPrefixMatcher(), parameters.getInvocationCount(), parameters.getCompletionType());
    ModCompletionResult consumer = new ModCompletionResult() {
      CompletionResultSet myResultSet = null;

      @Override
      public void accept(ModCompletionItem item) {
        CompletionResultSet res = myResultSet;
        if (res == null) {
          res = myResultSet = result.withRelevanceSorter(myProvider.getSorter(context));
        }
        res.addElement(new CompletionItemLookupElement(item));
      }
    };
    myProvider.provideItems(context, consumer);
  }

  @Override
  public @NotNull Class<?> getSubstitutedClass() {
    return myProvider.getClass();
  }

  @Override
  public String toString() {
    return "Adapter for " + myProvider;
  }
}
