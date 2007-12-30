/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupItemUtil;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.patterns.impl.PsiElementPattern;
import com.intellij.patterns.impl.StandardPatterns;
import com.intellij.psi.*;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.LinkedHashSet;
import java.util.HashSet;

/**
 * @author peter
 */
public class LegacyCompletionContributor extends CompletionContributor{
  public void registerCompletionProviders(final CompletionRegistrar registrar) {
    final CompletionProvider<LookupElement, CompletionParameters<LookupElement>> converter2LookupElement =
      new CompletionProvider<LookupElement, CompletionParameters<LookupElement>>() {
        public void addCompletions(@NotNull final CompletionEnvironment environment,
                                   @NotNull final CompletionQuery<LookupElement, CompletionParameters<LookupElement>> result) {
          result.clear();
          environment.query(new CompletionParameters<Object>(Object.class, result.getParameters().getPosition()))
            .forEach(new Processor<Object>() {
              public boolean process(final Object o) {
                result.addElement(LookupItemUtil.objectToLookupItem(o));
                return true;
              }
            });
        }
      };
    final PsiElementPattern._PsiElementPattern<PsiElement> position = StandardPatterns.psiElement();
    registrar.registerProvider(CompletionPlace.createPlace(LookupElement.class, position).setCompletionType(CompletionType.BASIC).setPriority(Double.NEGATIVE_INFINITY), converter2LookupElement);
    registrar.registerProvider(CompletionPlace.createPlace(LookupElement.class, position).setCompletionType(CompletionType.SMART).setPriority(Double.NEGATIVE_INFINITY), converter2LookupElement);
    registrar.registerProvider(CompletionPlace.createPlace(LookupElement.class, position).setCompletionType(CompletionType.CLASS_NAME).setPriority(Double.NEGATIVE_INFINITY), converter2LookupElement);

    registrar.registerProvider(
      CompletionPlace.createPlace(Object.class, position).setCompletionType(CompletionType.BASIC).setPriority(Double.POSITIVE_INFINITY),
      new CompletionProvider<Object, CompletionParameters<Object>>() {
        public void addCompletions(@NotNull final CompletionEnvironment environment, @NotNull final CompletionQuery<Object, CompletionParameters<Object>> result) {
          CompletionContext context = environment.getMatchingContext().get(CompletionContext.COMPLETION_CONTEXT_KEY);
          final PsiFile file = context.file;
          final PsiElement lastElement = file.findElementAt(context.startOffset - 1);
          PsiElement insertedElement = result.getParameters().getPosition();
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

    registrar.registerProvider(
      CompletionPlace.createPlace(Object.class, position).setCompletionType(CompletionType.SMART).setPriority(Double.POSITIVE_INFINITY),
      new CompletionProvider<Object, CompletionParameters<Object>>() {
        public void addCompletions(@NotNull final CompletionEnvironment environment, @NotNull final CompletionQuery<Object, CompletionParameters<Object>> result) {
          CompletionContext context = environment.getMatchingContext().get(CompletionContext.COMPLETION_CONTEXT_KEY);
          final PsiFile file = context.file;
          final PsiElement lastElement = file.findElementAt(context.startOffset - 1);
          PsiElement insertedElement = result.getParameters().getPosition();
          CompletionData completionData = CompletionUtil.getCompletionDataByElement(lastElement, context);
          context.setPrefix(insertedElement, context.startOffset, completionData);
          if (completionData == null) {
            // some completion data may depend on prefix
            completionData = CompletionUtil.getCompletionDataByElement(lastElement, context);
          }

          if (completionData == null) return;

          final Set<LookupItem> lookupSet = new LinkedHashSet<LookupItem>();
          if (insertedElement == null) return;
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
        }
      });

  }


}