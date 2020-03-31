/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNewExpression;
import com.intellij.util.IncorrectOperationException;

public class MissingArrayConstructorBracketFixer implements Fixer {
  @Override
  public void apply(Editor editor, JavaSmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException {
    if (!(psiElement instanceof PsiNewExpression)) return;
    PsiNewExpression expr = (PsiNewExpression)psiElement;
    int count = 0;
    for (PsiElement element = expr.getFirstChild(); element != null; element = element.getNextSibling()) {
      if (element.getNode().getElementType() == JavaTokenType.LBRACKET) {
        count++;
      } else if (element.getNode().getElementType() == JavaTokenType.RBRACKET) {
        count--;
      }
    }
    if (count > 0) {
      editor.getDocument().insertString(psiElement.getTextRange().getEndOffset(), "]");
    }
  }
}