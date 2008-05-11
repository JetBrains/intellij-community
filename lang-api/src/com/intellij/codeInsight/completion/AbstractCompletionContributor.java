/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

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
}
