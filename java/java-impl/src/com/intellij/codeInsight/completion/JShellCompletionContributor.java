// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.PsiJShellSyntheticElement;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene Zhuravlev
 */
public final class JShellCompletionContributor extends CompletionContributor implements DumbAware {
  @Override
  public void fillCompletionVariants(final @NotNull CompletionParameters parameters, final @NotNull CompletionResultSet resultSet) {
    resultSet.runRemainingContributors(parameters, r -> {
      if (!(r.getLookupElement().getPsiElement() instanceof PsiJShellSyntheticElement)) {
        resultSet.passResult(r);
      }
    });
  }

}
