// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.modcompletion.ModCompletionItem;
import com.intellij.modcompletion.ModCompletionItemFilter;
import com.intellij.modcompletion.ModCompletionItemProvider;
import com.intellij.modcompletion.ModCompletionResult;
import com.intellij.openapi.diagnostic.ReportingClassSubstitutor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A wrapper for {@link ModCompletionItemProvider} to be used as a {@link CompletionContributor}.
 */
@NotNullByDefault
final class CompletionItemContributor extends CompletionContributor implements ReportingClassSubstitutor {
  private final ModCompletionItemProvider myProvider;
  private final List<ModCompletionItemFilter> myFilters;

  @SuppressWarnings("NonDefaultConstructor")
  CompletionItemContributor(ModCompletionItemProvider provider, List<ModCompletionItemFilter> filters) {
    myProvider = provider;
    myFilters = filters;
  }
  
  @Override
  public void fillCompletionVariants(CompletionParameters parameters, CompletionResultSet result) {
    ModCompletionItemProvider.CompletionContext context = new ModCompletionItemProvider.CompletionContext(
      parameters.getOriginalFile(), parameters.getOffset(), parameters.getOriginalPosition(), parameters.getPosition(), 
      result.getPrefixMatcher(), parameters.getInvocationCount(), parameters.getCompletionType());
    ModCompletionResult consumer = new ModCompletionResult() {
      @Nullable CompletionResultSet myResultSet = null;

      @Override
      public void accept(ModCompletionItem item) {
        if (ContainerUtil.exists(myFilters, f -> !f.test(context, item))) return;
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
  public Class<?> getSubstitutedClass() {
    return myProvider.getClass();
  }

  @Override
  public String toString() {
    return "Adapter for " + myProvider;
  }
}
