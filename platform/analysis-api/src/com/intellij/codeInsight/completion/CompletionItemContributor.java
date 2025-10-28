// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcompletion.CompletionItemProvider;
import com.intellij.openapi.diagnostic.ReportingClassSubstitutor;
import org.jetbrains.annotations.NotNull;

/**
 * A wrapper for {@link CompletionItemProvider} to be used as a {@link CompletionContributor}.
 */
final class CompletionItemContributor extends CompletionContributor implements ReportingClassSubstitutor {
  private final CompletionItemProvider myProvider;

  @SuppressWarnings("NonDefaultConstructor")
  CompletionItemContributor(CompletionItemProvider provider) {
    myProvider = provider;
  }
  
  @Override
  public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
    ActionContext actionContext = ActionContext.from(parameters.getEditor(), parameters.getOriginalFile())
      .withElement(parameters.getPosition());
    CompletionItemProvider.CompletionContext context = new CompletionItemProvider.CompletionContext(
      actionContext, result.getPrefixMatcher().getPrefix(), parameters.getInvocationCount(), parameters.getCompletionType());
    myProvider.provideItems(context, item -> {
      result.addElement(new CompletionItemLookupElement(item));
    });
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
