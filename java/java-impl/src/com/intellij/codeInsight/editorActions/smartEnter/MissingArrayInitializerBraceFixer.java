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

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiArrayInitializerExpression;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Sep 5, 2003
 * Time: 7:24:03 PM
 * To change this template use Options | File Templates.
 */
public class MissingArrayInitializerBraceFixer implements Fixer {
  @Override
  public void apply(Editor editor, JavaSmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException {
    if (!(psiElement instanceof PsiArrayInitializerExpression)) return;
    PsiArrayInitializerExpression expr = (PsiArrayInitializerExpression)psiElement;
    final Document doc = editor.getDocument();
    final String exprText = expr.getText();
    final TextRange textRange = expr.getTextRange();
    final int endOffset = textRange.getEndOffset();
    int caretOffset = editor.getCaretModel().getOffset();
    final int startOffset = textRange.getStartOffset();
    if (caretOffset > startOffset && caretOffset < endOffset) {
      final int index = exprText.indexOf('\n', caretOffset - startOffset);
      if (index >= 0) {
        doc.insertString(index + startOffset, "}");
        return;
      }
    }
    if (!exprText.endsWith("}")) {
      doc.insertString(endOffset, "}");
    }
  }
}