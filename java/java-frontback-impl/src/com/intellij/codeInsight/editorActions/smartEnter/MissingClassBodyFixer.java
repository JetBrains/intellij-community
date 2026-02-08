// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiRecordComponent;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class MissingClassBodyFixer implements Fixer {

  @Override
  public void apply(Editor editor, JavaSmartEnterProcessor processor, @NotNull PsiElement psiElement)
    throws IncorrectOperationException {
    if (psiElement instanceof PsiRecordComponent recordComponent) {
      psiElement = recordComponent.getContainingClass();
    }
    if (!(psiElement instanceof PsiClass psiClass) || psiElement instanceof PsiTypeParameter) return;

    if (psiClass.getLBrace() == null) {
      PsiElement lastChild = psiClass.getLastChild();
      int offset = psiClass.getTextRange().getEndOffset();
      if (lastChild instanceof PsiErrorElement) {
        PsiElement previous = lastChild.getPrevSibling();
        if (previous instanceof PsiWhiteSpace) {
          offset = previous.getTextRange().getStartOffset();
        }
        else {
          offset = lastChild.getTextRange().getStartOffset();
        }
      }
      if (psiClass.isRecord() && psiClass.getRecordHeader() == null) {
        editor.getDocument().insertString(offset, "() {}");
        editor.getCaretModel().moveToOffset(offset + 1);
        processor.setSkipEnter(true);
      }
      else {
        editor.getDocument().insertString(offset, "{\n}");
        editor.getCaretModel().moveToOffset(offset);
      }
    }
  }
}
