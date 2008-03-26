/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.patterns.ElementPattern;
import static com.intellij.patterns.PlatformPatterns.psiElement;
import com.intellij.patterns.PsiJavaPatterns;
import static com.intellij.patterns.StandardPatterns.or;
import com.intellij.psi.*;
import com.intellij.util.ProcessingContext;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * @author peter
 */
public class JavaMemberNameCompletionContributor extends CompletionContributor{
  @NonNls public static final String VARIABLE_NAME = "VARIABLE_NAME";
  @NonNls public static final String METHOD_NAME = "METHOD_NAME";
  private static final ElementPattern INSIDE_TYPE_PARAMS_PATTERN = psiElement().afterLeaf(psiElement().withText("?").afterLeaf(psiElement().withText("<")));


  public void registerCompletionProviders(final CompletionRegistrar registrar) {
    registrar.extend(
        CompletionType.BASIC,
        psiElement(PsiIdentifier.class).andNot(INSIDE_TYPE_PARAMS_PATTERN).withParent(
            or(psiElement(PsiLocalVariable.class), psiElement(PsiParameter.class))),
        new CompletionProvider<LookupElement, CompletionParameters>() {
          public void addCompletions(@NotNull final CompletionParameters parameters, final ProcessingContext matchingContext, @NotNull final CompletionResultSet<LookupElement> result) {
            final Set<LookupItem> lookupSet = new THashSet<LookupItem>();
            ApplicationManager.getApplication().runReadAction(new Runnable() {
              public void run() {
                JavaCompletionUtil.completeLocalVariableName(lookupSet, result.getPrefixMatcher(), (PsiVariable)parameters.getPosition().getParent());
              }
            });
            for (final LookupItem item : lookupSet) {
              result.addElement(item);
            }
          }
        });
    registrar.extend(
        CompletionType.BASIC, psiElement(PsiIdentifier.class).withParent(PsiField.class), new CompletionProvider<LookupElement, CompletionParameters>() {
      public void addCompletions(@NotNull final CompletionParameters parameters, final ProcessingContext matchingContext, @NotNull final CompletionResultSet<LookupElement> result) {
        final Set<LookupItem> lookupSet = new THashSet<LookupItem>();
        ApplicationManager.getApplication().runReadAction(new Runnable() {
          public void run() {
            final PsiVariable variable = (PsiVariable)parameters.getPosition().getParent();
            JavaCompletionUtil.completeFieldName(lookupSet, variable, result.getPrefixMatcher());
            JavaCompletionUtil.completeMethodName(lookupSet, variable, result.getPrefixMatcher());
          }
        });
        for (final LookupItem item : lookupSet) {
          result.addElement(item);
        }
      }
    });
    registrar.extend(
        CompletionType.BASIC, PsiJavaPatterns.psiElement().nameIdentifierOf(PsiJavaPatterns.psiMethod().withParent(PsiClass.class)),
        new CompletionProvider<LookupElement, CompletionParameters>() {
          public void addCompletions(@NotNull final CompletionParameters parameters, final ProcessingContext matchingContext, @NotNull final CompletionResultSet<LookupElement> result) {
            final Set<LookupItem> lookupSet = new THashSet<LookupItem>();
            ApplicationManager.getApplication().runReadAction(new Runnable() {
              public void run() {
                JavaCompletionUtil.completeMethodName(lookupSet, parameters.getPosition().getParent(), result.getPrefixMatcher());
              }
            });
            for (final LookupItem item : lookupSet) {
              result.addElement(item);
            }
          }
        });

  }

}