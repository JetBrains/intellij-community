/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author peter
 */
public class LegacyCompletionContributor extends CompletionContributor{
  @NonNls public static final String LEGACY = "Legacy";

  public void registerCompletionProviders(final CompletionRegistrar registrar) {
    final PsiElementPattern.Capture<PsiElement> everywhere = PlatformPatterns.psiElement();
    registrar.extend(CompletionType.BASIC, everywhere).withId(LEGACY).withProvider(new CompletionProvider<LookupElement, CompletionParameters>() {
      public void addCompletions(@NotNull final CompletionParameters parameters, final ProcessingContext matchingContext, @NotNull final CompletionResultSet<LookupElement> result) {
        CompletionContext context = parameters.getPosition().getUserData(CompletionContext.COMPLETION_CONTEXT_KEY);
        final PsiFile file = parameters.getOriginalFile();
        final int startOffset = context.startOffset;
        final PsiElement lastElement = file.findElementAt(startOffset - 1);
        PsiElement insertedElement = parameters.getPosition();
        CompletionData completionData = CompletionUtil.getCompletionDataByElement(lastElement, file, startOffset);
        result.setPrefixMatcher(completionData == null ? CompletionData.findPrefixStatic(insertedElement, startOffset) : completionData.findPrefix(insertedElement, startOffset));
        if (completionData == null) {
          // some completion data may depend on prefix
          completionData = CompletionUtil.getCompletionDataByElement(lastElement, file, startOffset);
        }

        if (completionData == null) return;

        final Set<LookupItem> lookupSet = new LinkedHashSet<LookupItem>();
        final PsiReference ref = insertedElement.getContainingFile().findReferenceAt(context.offset);
        if (ref != null) {
          completionData.completeReference(ref, lookupSet, insertedElement, result.getPrefixMatcher(), context.file, context.offset);
        }
        if (lookupSet.isEmpty() || !CodeInsightUtil.isAntFile(file)) {
          final Set<CompletionVariant> keywordVariants = new HashSet<CompletionVariant>();
          completionData.addKeywordVariants(keywordVariants, insertedElement, context.file);
          CompletionData.completeKeywordsBySet(lookupSet, keywordVariants, insertedElement, result.getPrefixMatcher(), context.file);
        }
        result.addAllElements(lookupSet);
      }
    });


  }


}