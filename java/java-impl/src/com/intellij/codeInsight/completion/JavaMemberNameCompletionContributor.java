/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
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
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * @author peter
 */
public class JavaMemberNameCompletionContributor extends CompletionContributor {
  public static final ElementPattern<PsiElement> INSIDE_TYPE_PARAMS_PATTERN =
    psiElement().afterLeaf(psiElement().withText("?").afterLeaf("<", ","));

  public JavaMemberNameCompletionContributor() {
    extend(
        CompletionType.BASIC,
        psiElement(PsiIdentifier.class).andNot(INSIDE_TYPE_PARAMS_PATTERN).withParent(
            or(psiElement(PsiLocalVariable.class), psiElement(PsiParameter.class))),
        new CompletionProvider<CompletionParameters>() {
          public void addCompletions(@NotNull final CompletionParameters parameters, final ProcessingContext matchingContext, @NotNull final CompletionResultSet result) {
            final Set<LookupElement> lookupSet = new THashSet<LookupElement>();
            ApplicationManager.getApplication().runReadAction(new Runnable() {
              public void run() {
                JavaCompletionUtil.completeLocalVariableName(lookupSet, result.getPrefixMatcher(), (PsiVariable)parameters.getPosition().getParent());
              }
            });
            for (final LookupElement item : lookupSet) {
              if (item instanceof LookupItem) {
                ((LookupItem)item).setAutoCompletionPolicy(AutoCompletionPolicy.GIVE_CHANCE_TO_OVERWRITE);
              }
              result.addElement(item);
            }
          }
        });
    extend(
        CompletionType.BASIC,
        psiElement(PsiIdentifier.class).withParent(PsiField.class).andNot(INSIDE_TYPE_PARAMS_PATTERN),
        new CompletionProvider<CompletionParameters>() {
      public void addCompletions(@NotNull final CompletionParameters parameters, final ProcessingContext matchingContext, @NotNull final CompletionResultSet result) {
        final Set<LookupElement> lookupSet = new THashSet<LookupElement>();
        ApplicationManager.getApplication().runReadAction(new Runnable() {
          public void run() {
            final PsiVariable variable = (PsiVariable)parameters.getPosition().getParent();
            JavaCompletionUtil.completeFieldName(lookupSet, variable, result.getPrefixMatcher());
            JavaCompletionUtil.completeMethodName(lookupSet, variable, result.getPrefixMatcher());
          }
        });
        for (final LookupElement item : lookupSet) {
          result.addElement(item);
        }
      }
    });
    extend(
        CompletionType.BASIC, PsiJavaPatterns.psiElement().nameIdentifierOf(PsiJavaPatterns.psiMethod().withParent(PsiClass.class)),
        new CompletionProvider<CompletionParameters>() {
          public void addCompletions(@NotNull final CompletionParameters parameters, final ProcessingContext matchingContext, @NotNull final CompletionResultSet result) {
            final Set<LookupElement> lookupSet = new THashSet<LookupElement>();
            ApplicationManager.getApplication().runReadAction(new Runnable() {
              public void run() {
                JavaCompletionUtil.completeMethodName(lookupSet, parameters.getPosition().getParent(), result.getPrefixMatcher());
              }
            });
            for (final LookupElement item : lookupSet) {
              result.addElement(item);
            }
          }
        });

  }

}
