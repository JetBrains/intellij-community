/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.compiler.classFilesIndex.chainsSearch.completion;

import com.intellij.codeInsight.completion.CompletionInitializationContext;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.java.PsiMethodCallExpressionImpl;

import static com.intellij.patterns.PsiJavaPatterns.psiElement;
import static com.intellij.patterns.StandardPatterns.or;

/**
 * @author Dmitry Batkovich <dmitry.batkovich@jetbrains.com>
 */
public final class CompletionContributorPatternUtil {

  private CompletionContributorPatternUtil() {}

  @SuppressWarnings("unchecked")
  public static ElementPattern<PsiElement> patternForVariableAssignment() {
    final ElementPattern<PsiElement> patternForParent = or(psiElement().withText(CompletionInitializationContext.DUMMY_IDENTIFIER_TRIMMED)
                                                             .afterSiblingSkipping(psiElement(PsiWhiteSpace.class),
                                                                                   psiElement(PsiJavaToken.class).withText("=")));

    return psiElement().withParent(patternForParent).withSuperParent(2, or(psiElement(PsiAssignmentExpression.class),
                                                                           psiElement(PsiLocalVariable.class)
                                                                             .inside(PsiDeclarationStatement.class)))
                                                                             .inside(PsiMethod.class);
  }

  public static ElementPattern<PsiElement> patternForMethodParameter() {
    return psiElement().withSuperParent(3, PsiMethodCallExpressionImpl.class);
  }
}
