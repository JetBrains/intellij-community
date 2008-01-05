/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.patterns.impl.PsiElementPattern;
import com.intellij.patterns.impl.StandardPatterns;
import com.intellij.patterns.impl.MatchingContext;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.util.QueryResultSet;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author peter
 */
public class LegacyCompletionContributor extends CompletionContributor{
  public void registerCompletionProviders(final CompletionRegistrar registrar) {
    final PsiElementPattern._PsiElementPattern<PsiElement> everywhere = StandardPatterns.psiElement();
    registrar.extendBasicCompletion(everywhere).onPriority(Double.POSITIVE_INFINITY).withProvider(new CompletionProvider<LookupElement, CompletionParameters>() {
      public void addCompletions(@NotNull final CompletionParameters parameters, final MatchingContext matchingContext, @NotNull final QueryResultSet<LookupElement> result) {
        CompletionContext context = parameters.getPosition().getUserData(CompletionContext.COMPLETION_CONTEXT_KEY);
        final PsiFile file = context.file;
        final PsiElement lastElement = file.findElementAt(context.startOffset - 1);
        PsiElement insertedElement = parameters.getPosition();
        CompletionData completionData = CompletionUtil.getCompletionDataByElement(lastElement, context);
        context.setPrefix(insertedElement, context.startOffset, completionData);
        if (completionData == null) {
          // some completion data may depend on prefix
          completionData = CompletionUtil.getCompletionDataByElement(lastElement, context);
        }

        if (completionData == null) return;
        if (insertedElement == null) return;

        final Set<LookupItem> lookupSet = new LinkedHashSet<LookupItem>();
        final PsiReference ref = insertedElement.getContainingFile().findReferenceAt(context.offset);
        if (ref != null) {
          completionData.completeReference(ref, lookupSet, context, insertedElement);
        }
        if (lookupSet.isEmpty() || !CodeInsightUtil.isAntFile(file)) {
          final Set<CompletionVariant> keywordVariants = new HashSet<CompletionVariant>();
          completionData.addKeywordVariants(keywordVariants, context, insertedElement);
          CompletionData.completeKeywordsBySet(lookupSet, keywordVariants, context, insertedElement);
          CompletionUtil.highlightMembersOfContainer(lookupSet);
        }
        result.addAllElements(lookupSet);
      }
    });

  }


}