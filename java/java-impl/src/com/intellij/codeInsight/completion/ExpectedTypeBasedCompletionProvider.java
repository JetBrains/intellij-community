/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.util.ProcessingContext;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;

/**
 * @author peter
*/
public abstract class ExpectedTypeBasedCompletionProvider extends CompletionProvider<CompletionParameters> {

  public ExpectedTypeBasedCompletionProvider() {
    super(false);
  }

  public void addCompletions(@NotNull final CompletionParameters params, final ProcessingContext matchingContext, @NotNull final CompletionResultSet result) {
    final PsiElement position = params.getPosition();
    if (position.getParent() instanceof PsiLiteralExpression) return;

    final THashSet<ExpectedTypeInfo> infos = new THashSet<ExpectedTypeInfo>();
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        infos.addAll(Arrays.asList(JavaSmartCompletionContributor.getExpectedTypes(params)));
      }
    });
    addCompletions(params, result, infos);
  }

  protected abstract void addCompletions(CompletionParameters params, CompletionResultSet result, Collection<ExpectedTypeInfo> infos);
}
