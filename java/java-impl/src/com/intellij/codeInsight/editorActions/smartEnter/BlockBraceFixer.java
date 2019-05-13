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
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiStatement;
import com.intellij.util.IncorrectOperationException;

public class BlockBraceFixer implements Fixer{
  @Override
  public void apply(Editor editor, JavaSmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException {
    if (psiElement instanceof PsiCodeBlock && afterUnmatchedBrace(editor,psiElement.getContainingFile().getFileType())) {
      PsiCodeBlock block = (PsiCodeBlock) psiElement;
      int stopOffset = block.getTextRange().getEndOffset();
      final PsiStatement[] statements = block.getStatements();
      if (statements.length > 0) {
        stopOffset = statements[0].getTextRange().getEndOffset();
      }
      editor.getDocument().insertString(stopOffset, "}");
    }
  }

  private boolean afterUnmatchedBrace(Editor editor, FileType fileType) {
    return EnterAfterUnmatchedBraceHandler.isAfterUnmatchedLBrace(editor, editor.getCaretModel().getOffset(), fileType);
  }
}
