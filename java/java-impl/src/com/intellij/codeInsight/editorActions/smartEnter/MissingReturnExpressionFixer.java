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
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Sep 8, 2003
 * Time: 4:20:36 PM
 * To change this template use Options | File Templates.
 */
@SuppressWarnings({"HardCodedStringLiteral"})
public class MissingReturnExpressionFixer implements Fixer {
  public void apply(Editor editor, JavaSmartEnterProcessor processor, PsiElement psiElement)
      throws IncorrectOperationException {
    if (psiElement instanceof PsiReturnStatement) {
      PsiReturnStatement retStatement = (PsiReturnStatement) psiElement;
      PsiExpression returnValue = retStatement.getReturnValue();
      if (returnValue != null &&
          lineNumber(editor, editor.getCaretModel().getOffset()) == lineNumber(editor, returnValue.getTextRange().getStartOffset())) {
        return;
      }

      PsiElement parent = PsiTreeUtil.getParentOfType(psiElement, PsiClassInitializer.class, PsiMethod.class);
      if (parent instanceof PsiMethod) {
        final PsiType returnType = ((PsiMethod) parent).getReturnType();
        if (returnType != null && returnType != PsiType.VOID) {
          final int startOffset = retStatement.getTextRange().getStartOffset();
          if (returnValue != null) {
            editor.getDocument().insertString(startOffset + "return".length(), ";");
          }

          processor.registerUnresolvedError(startOffset + "return".length());
        }
      }
    }
  }

  private static int lineNumber(Editor editor, int offset) {
    return editor.getDocument().getLineNumber(offset);
  }
}
