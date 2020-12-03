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
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public class MethodCallFixer implements Fixer {
  @Override
  public void apply(Editor editor, JavaSmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException {
    PsiExpressionList argList = null;
    if (psiElement instanceof PsiMethodCallExpression && !(psiElement instanceof JspMethodCall)) {
      argList = ((PsiMethodCallExpression) psiElement).getArgumentList();
    } else if (psiElement instanceof PsiNewExpression) {
      argList = ((PsiNewExpression) psiElement).getArgumentList();
    }

    int caret = editor.getCaretModel().getOffset();
    if (argList != null && !hasRParenth(argList)) {
      PsiCallExpression innermostCall = PsiTreeUtil.findElementOfClassAtOffset(psiElement.getContainingFile(), caret - 1, PsiCallExpression.class, false);
      if (innermostCall == null) return;

      argList = innermostCall.getArgumentList();
      if (argList == null) return;

      int endOffset = -1;
      PsiElement child = argList.getFirstChild();
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
        endOffset = argList.getTextRange().getEndOffset();
      }

      PsiExpression[] args = argList.getExpressions();
      if (args.length > 0 &&
          startLine(editor, argList) != startLine(editor, args[0]) &&
          caret < args[0].getTextRange().getStartOffset()) {
        endOffset = argList.getTextRange().getStartOffset() + 1;
      }

      if (!DumbService.isDumb(argList.getProject())) {
        int caretArg = ContainerUtil.indexOf(Arrays.asList(args), arg -> arg.getTextRange().containsOffset(caret));
        Integer argCount = getMinimalParameterCount(innermostCall);
        if (argCount != null && argCount > 0 && argCount < args.length) {
          endOffset = Math.min(endOffset, args[Math.max(argCount - 1, caretArg)].getTextRange().getEndOffset());
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
  private static Integer getMinimalParameterCount(PsiCallExpression call) {
    int paramCount = Integer.MAX_VALUE;
    for (CandidateInfo candidate : PsiResolveHelper.SERVICE.getInstance(call.getProject()).getReferencedMethodCandidates(call, false)) {
      PsiElement element = candidate.getElement();
      if (element instanceof PsiMethod && !((PsiMethod)element).isVarArgs()) {
        paramCount = Math.min(paramCount, ((PsiMethod)element).getParameterList().getParametersCount());
      }
    }
    return paramCount == Integer.MAX_VALUE ? null : paramCount;
  }

  private static int startLine(Editor editor, PsiElement psiElement) {
    return editor.getDocument().getLineNumber(psiElement.getTextRange().getStartOffset());
  }
}
