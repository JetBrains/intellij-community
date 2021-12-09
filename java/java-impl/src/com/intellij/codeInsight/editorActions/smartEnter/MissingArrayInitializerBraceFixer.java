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
package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.codeInsight.editorActions.enter.EnterAfterUnmatchedBraceHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;

public class MissingArrayInitializerBraceFixer implements Fixer {
  @Override
  public void apply(Editor editor, JavaSmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException {
    if (!(psiElement instanceof PsiArrayInitializerExpression || psiElement instanceof PsiArrayInitializerMemberValue)) return;
    PsiElement child = psiElement.getFirstChild();
    if (!PsiUtil.isJavaToken(child, JavaTokenType.LBRACE)) return;
    if (!EnterAfterUnmatchedBraceHandler.isAfterUnmatchedLBrace(editor, child.getTextRange().getEndOffset(),
                                                                psiElement.getContainingFile().getFileType())) return;
    PsiElement anchor = PsiTreeUtil.getChildOfType(psiElement, PsiErrorElement.class);
    if (anchor == null) {
      PsiElement last = PsiTreeUtil.getDeepestVisibleLast(psiElement);
      while (PsiUtil.isJavaToken(last, JavaTokenType.RBRACE)) {
        last = PsiTreeUtil.prevCodeLeaf(last);
      }
      if (last != null && PsiTreeUtil.isAncestor(psiElement, last, true)) {
        anchor = last;
      }
    }
    int endOffset = (anchor != null ? anchor : psiElement).getTextRange().getEndOffset();
    editor.getDocument().insertString(endOffset, "}");
  }
}