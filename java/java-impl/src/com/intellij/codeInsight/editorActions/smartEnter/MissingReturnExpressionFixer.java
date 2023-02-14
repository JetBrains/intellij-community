// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings({"HardCodedStringLiteral"})
public class MissingReturnExpressionFixer implements Fixer {
  @Override
  public void apply(Editor editor, JavaSmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException {
    if (!(psiElement instanceof PsiReturnStatement retStatement)) {
      return;
    }
    if (!PsiTreeUtil.hasErrorElements(psiElement)) {
      return;
    }

    if (fixMethodCallWithoutTrailingSemicolon(retStatement, editor, processor)) {
      return;
    }

    PsiExpression returnValue = retStatement.getReturnValue();
    if (returnValue != null
        && lineNumber(editor, editor.getCaretModel().getOffset()) == lineNumber(editor, returnValue.getTextRange().getStartOffset()))
    {
      return;
    }

    PsiElement parent = PsiTreeUtil.getParentOfType(psiElement, PsiClassInitializer.class, PsiMethod.class);
    if (parent instanceof PsiMethod) {
      final PsiType returnType = ((PsiMethod)parent).getReturnType();
      if (returnType != null && !PsiTypes.voidType().equals(returnType)) {
        final int startOffset = retStatement.getTextRange().getStartOffset();
        if (returnValue != null) {
          editor.getDocument().insertString(startOffset + "return".length(), ";");
        }

        processor.registerUnresolvedError(startOffset + "return".length());
      }
    }
  }

  private static boolean fixMethodCallWithoutTrailingSemicolon(@Nullable PsiReturnStatement returnStatement, @NotNull Editor editor,
                                                               @NotNull JavaSmartEnterProcessor processor)
  {
    if (returnStatement == null) {
      return false;
    }
    final PsiElement lastChild = returnStatement.getLastChild();
    if (!(lastChild instanceof PsiErrorElement)) {
      return false;
    }
    PsiElement prev = lastChild.getPrevSibling();
    if (prev instanceof PsiWhiteSpace) {
      prev = prev.getPrevSibling();
    }
    
    if (!(prev instanceof PsiJavaToken prevToken)) {
      int offset = returnStatement.getTextRange().getEndOffset();
      final PsiMethod method = PsiTreeUtil.getParentOfType(returnStatement, PsiMethod.class, true, PsiLambdaExpression.class);
      if (method != null && PsiTypes.voidType().equals(method.getReturnType())) {
        offset = returnStatement.getTextRange().getStartOffset() + "return".length();
      } 
      editor.getDocument().insertString(offset, ";");
      //processor.setSkipEnter(true);
      return true;
    }

    if (prevToken.getTokenType() == JavaTokenType.SEMICOLON) {
      return false;
    }

    final int offset = returnStatement.getTextRange().getEndOffset();
    editor.getDocument().insertString(offset, ";");
    if (prevToken.getTokenType() == JavaTokenType.RETURN_KEYWORD) {
      final PsiMethod method = PsiTreeUtil.getParentOfType(returnStatement, PsiMethod.class);
      if (method != null && !PsiTypes.voidType().equals(method.getReturnType())) {
        editor.getCaretModel().moveToOffset(offset);
        processor.setSkipEnter(true);
      }
    }
    return true;
  }
  
  private static int lineNumber(Editor editor, int offset) {
    return editor.getDocument().getLineNumber(offset);
  }
}
