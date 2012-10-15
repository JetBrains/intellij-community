/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.codeInsight.TailType;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;

import static com.intellij.patterns.PsiJavaPatterns.psiClass;
import static com.intellij.patterns.PsiJavaPatterns.psiElement;

public class Java18CompletionData extends Java15CompletionData {
  private static final PsiElementPattern<PsiElement, ?> AFTER_PARENTH_IN_EXT_METHOD = psiElement()
    .afterLeaf(psiElement(JavaTokenType.RPARENTH).withParent(PsiParameterList.class))
    .withSuperParent(3, psiClass().isInterface().nonAnnotationType());

  private static final PsiElementPattern<PsiElement, ?> AFTER_DOUBLE_COLON = psiElement()
    .afterLeaf(psiElement(JavaTokenType.DOUBLE_COLON));

  @Override
  public void fillCompletions(final CompletionParameters parameters, final CompletionResultSet result) {
    final PsiElement position = parameters.getPosition();

    if (!inComment(position)) {
      if (AFTER_PARENTH_IN_EXT_METHOD.accepts(position)) {
        result.addElement(new OverrideableSpace(createKeyword(position, PsiKeyword.DEFAULT), TailType.SPACE));
        return;
      }

      if (AFTER_DOUBLE_COLON.accepts(position)) {
        final PsiMethodReferenceExpression parent = PsiTreeUtil.getParentOfType(parameters.getPosition(), PsiMethodReferenceExpression.class);
        final TailType tailType = parent != null && !LambdaUtil.insertSemicolon(parent.getParent()) ? TailType.SEMICOLON : TailType.NONE;
        result.addElement(new OverrideableSpace(createKeyword(position, PsiKeyword.NEW), tailType));
        return;
      }
    }

    super.fillCompletions(parameters, result);
  }

  private static boolean inComment(final PsiElement position) {
    return PsiTreeUtil.getParentOfType(position, PsiComment.class, false) != null;
  }
}
