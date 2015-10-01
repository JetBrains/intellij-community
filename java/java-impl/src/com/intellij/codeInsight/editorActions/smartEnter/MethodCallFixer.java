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

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbService;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.jsp.jspJava.JspMethodCall;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Sep 5, 2003
 * Time: 3:35:49 PM
 * To change this template use Options | File Templates.
 */
public class MethodCallFixer implements Fixer {
  @Override
  public void apply(Editor editor, JavaSmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException {
    PsiExpressionList args = null;
    if (psiElement instanceof PsiMethodCallExpression && !(psiElement instanceof JspMethodCall)) {
      args = ((PsiMethodCallExpression) psiElement).getArgumentList();
    } else if (psiElement instanceof PsiNewExpression) {
      args = ((PsiNewExpression) psiElement).getArgumentList();
    }

    if (args != null && !hasRParenth(args)) {
      int caret = editor.getCaretModel().getOffset();
      PsiCallExpression innermostCall = PsiTreeUtil.findElementOfClassAtOffset(psiElement.getContainingFile(), caret - 1, PsiCallExpression.class, false);
      if (innermostCall == null) return;

      args = innermostCall.getArgumentList();
      if (args == null) return;

      int endOffset = -1;
      PsiElement child = args.getFirstChild();
      while (child != null) {
        if (child instanceof PsiErrorElement) {
          final PsiErrorElement errorElement = (PsiErrorElement)child;
          if (errorElement.getErrorDescription().contains("')'")) {
            endOffset = errorElement.getTextRange().getStartOffset();
            break;
          }
        }
        child = child.getNextSibling();
      }

      if (endOffset == -1) {
        endOffset = args.getTextRange().getEndOffset();
      }

      final PsiExpression[] params = args.getExpressions();
      if (params.length > 0 && 
          startLine(editor, args) != startLine(editor, params[0]) &&
          caret < params[0].getTextRange().getStartOffset()) {
        endOffset = args.getTextRange().getStartOffset() + 1;
      }

      if (!DumbService.isDumb(args.getProject())) {
        Integer argCount = getUnambiguousParameterCount(innermostCall);
        if (argCount != null && argCount > 0 && argCount < params.length) {
          endOffset = Math.min(endOffset, params[argCount - 1].getTextRange().getEndOffset());
        }
      }

      endOffset = CharArrayUtil.shiftBackward(editor.getDocument().getCharsSequence(), endOffset - 1, " \t\n") + 1;
      editor.getDocument().insertString(endOffset, ")");
    }
  }

  private static boolean hasRParenth(PsiExpressionList args) {
    PsiElement parenth = args.getLastChild();
    return parenth != null && ")".equals(parenth.getText());
  }

  @Nullable
  private static Integer getUnambiguousParameterCount(PsiCallExpression call) {
    int argCount = -1;
    for (CandidateInfo candidate : PsiResolveHelper.SERVICE.getInstance(call.getProject()).getReferencedMethodCandidates(call, false)) {
      PsiElement element = candidate.getElement();
      if (!(element instanceof PsiMethod)) return null;
      if (((PsiMethod)element).isVarArgs()) return null;

      int count = ((PsiMethod)element).getParameterList().getParametersCount();
      if (argCount == -1) {
        argCount = count;
      } else if (argCount != count) {
        return null;
      }
    }
    return argCount;
  }

  private static int startLine(Editor editor, PsiElement psiElement) {
    return editor.getDocument().getLineNumber(psiElement.getTextRange().getStartOffset());
  }
}
