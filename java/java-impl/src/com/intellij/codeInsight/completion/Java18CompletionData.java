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

import static com.intellij.patterns.PsiJavaPatterns.psiElement;

public class Java18CompletionData extends Java15CompletionData {
  private static final PsiElementPattern<PsiElement, ?> AFTER_DOUBLE_COLON = psiElement()
    .afterLeaf(psiElement(JavaTokenType.DOUBLE_COLON));

  @Override
  public void fillCompletions(final CompletionParameters parameters, final CompletionResultSet result) {
    PsiElement position = parameters.getPosition();

    if (!inComment(position)) {
      if (AFTER_DOUBLE_COLON.accepts(position)) {
        PsiMethodReferenceExpression parent = PsiTreeUtil.getParentOfType(parameters.getPosition(), PsiMethodReferenceExpression.class);
        TailType tail = parent != null && !LambdaHighlightingUtil.insertSemicolon(parent.getParent()) ? TailType.SEMICOLON : TailType.NONE;
        result.addElement(new OverrideableSpace(createKeyword(position, PsiKeyword.NEW), tail));
        return;
      }

      if (isSuitableForClass(position)) {
        PsiElement scope = position.getParent();
        while (scope != null && !(scope instanceof PsiFile)) {
          if (scope instanceof PsiClass && ((PsiClass)scope).isInterface()) {
            result.addElement(new OverrideableSpace(createKeyword(position, PsiKeyword.DEFAULT), TailType.HUMBLE_SPACE_BEFORE_WORD));
            break;
          }
          scope = scope.getParent();
        }
      }
    }

    super.fillCompletions(parameters, result);
  }

  private static boolean inComment(final PsiElement position) {
    return PsiTreeUtil.getParentOfType(position, PsiComment.class, false) != null;
  }
}
