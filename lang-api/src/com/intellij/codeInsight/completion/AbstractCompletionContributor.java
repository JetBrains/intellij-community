/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.psi.PsiElement;
import com.intellij.patterns.ElementPattern;
import com.intellij.util.ProcessingContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;

/**
 * @author peter
 */
public abstract class AbstractCompletionContributor<Params extends CompletionParameters> {

  /**
   * Fill completion variants.
   *
   * Invoked sequentially for all registered contributors in order of registration.
   *
   * @param parameters
   * @param result
   * @return Whether to continue variants collecting process. If false, remaining non-visited completion contributors are ignored.
   * @see CompletionService#getVariantsFromContributors(com.intellij.openapi.extensions.ExtensionPointName, CompletionParameters, AbstractCompletionContributor , com.intellij.util.Consumer)
   */
  public abstract boolean fillCompletionVariants(Params parameters, CompletionResultSet result);

  protected static boolean isPatternSuitable(final ElementPattern<? extends PsiElement> pattern, final CompletionParameters parameters,
                                             final ProcessingContext context) {
    return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      public Boolean compute() {
        return pattern.accepts(parameters.getPosition(), context);
      }
    }).booleanValue();
  }
}
