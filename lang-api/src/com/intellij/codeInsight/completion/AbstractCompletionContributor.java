/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.MultiMap;

/**
 * @author peter
 */
public abstract class AbstractCompletionContributor<Params extends CompletionParameters> {
  private final MultiMap<CompletionType, Pair<ElementPattern<? extends PsiElement>, CompletionProvider<Params>>> myMap =
      new MultiMap<CompletionType, Pair<ElementPattern<? extends PsiElement>, CompletionProvider<Params>>>();

  public final void extend(CompletionType type, final ElementPattern<? extends PsiElement> place, CompletionProvider<Params> provider) {
    myMap.putValue(type, new Pair<ElementPattern<? extends PsiElement>, CompletionProvider<Params>>(place, provider));
  }

  /**
   * Fill completion variants.
   *
   * Invoked sequentially for all registered contributors in order of registration.
   *
   * @param parameters
   * @param result
   * @return Whether to continue variants collecting process. If false, remaining non-visited completion contributors are ignored.
   * @see CompletionService#getVariantsFromContributors(com.intellij.openapi.extensions.ExtensionPointName, CompletionParameters, AbstractCompletionContributor, com.intellij.util.Consumer) 
   */
  public boolean fillCompletionVariants(final Params parameters, CompletionResultSet result) {
    for (final Pair<ElementPattern<? extends PsiElement>, CompletionProvider<Params>> pair : myMap.get(parameters.getCompletionType())) {
      final ProcessingContext context = new ProcessingContext();
      if (ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
        public Boolean compute() {
          return pair.first.accepts(parameters.getPosition(), context);
        }
      }).booleanValue()) {
        if (!pair.second.addCompletionVariants(parameters, context, result)) return false;
      }
    }
    return true;
  }

}
