/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.editorActions.enter.EnterAfterUnmatchedBraceHandler;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiExpressionListStatement;

public class JavaEnterAfterUnmatchedBraceHandler extends EnterAfterUnmatchedBraceHandler {
  @Override
  protected int calculateOffsetToInsertClosingBraceInsideElement(PsiElement element) {
    if (element instanceof PsiExpressionListStatement) {
      final PsiExpressionList list = ((PsiExpressionListStatement)element).getExpressionList();
      if (list != null) {
        final PsiExpression[] expressions = list.getExpressions();
        if (expressions.length > 1) {
          return expressions[0].getTextRange().getEndOffset();
        }
      }
    }
    return super.calculateOffsetToInsertClosingBraceInsideElement(element);
  }
}
