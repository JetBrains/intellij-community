/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import org.jetbrains.annotations.NotNull;
import com.intellij.util.ProcessingContext;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.openapi.application.ApplicationManager;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;

import java.util.Arrays;
import java.util.Collection;

/**
 * @author peter
*/
public abstract class ExpectedTypeBasedCompletionProvider extends CompletionProvider<CompletionParameters> {
  private static final TObjectHashingStrategy<ExpectedTypeInfo> EXPECTED_TYPE_INFO_STRATEGY = new TObjectHashingStrategy<ExpectedTypeInfo>() {
    public int computeHashCode(final ExpectedTypeInfo object) {
      return object.getType().hashCode();
    }

    public boolean equals(final ExpectedTypeInfo o1, final ExpectedTypeInfo o2) {
      return o1.getType().equals(o2.getType());
    }
  };

  public ExpectedTypeBasedCompletionProvider(final boolean returnValue) {
    super(returnValue, false);
  }

  public void addCompletions(@NotNull final CompletionParameters params, final ProcessingContext matchingContext, @NotNull final CompletionResultSet result) {
    final PsiElement position = params.getPosition();
    if (position.getParent() instanceof PsiLiteralExpression) return;

    final THashSet<ExpectedTypeInfo> infos = new THashSet<ExpectedTypeInfo>(EXPECTED_TYPE_INFO_STRATEGY);
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        infos.addAll(Arrays.asList(JavaSmartCompletionContributor.getExpectedTypes(position)));
      }
    });
    addCompletions(params, result, infos);
  }

  protected abstract void addCompletions(CompletionParameters params, CompletionResultSet result, Collection<ExpectedTypeInfo> infos);
}
