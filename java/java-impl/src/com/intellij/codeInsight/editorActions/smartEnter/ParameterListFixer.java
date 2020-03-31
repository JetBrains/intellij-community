// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.editorActions.smartEnter;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiAnnotationParameterList;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiParameterList;
import com.intellij.util.IncorrectOperationException;

public class ParameterListFixer implements Fixer {
  @Override
  public void apply(Editor editor, JavaSmartEnterProcessor processor, PsiElement psiElement) throws IncorrectOperationException {
    if (psiElement instanceof PsiParameterList || psiElement instanceof PsiAnnotationParameterList) {
      String text = psiElement.getText();
      if (StringUtil.startsWithChar(text, '(') && !StringUtil.endsWithChar(text, ')')) {
        PsiElement[] params = psiElement instanceof PsiParameterList ? ((PsiParameterList)psiElement).getParameters()
                                                                     : ((PsiAnnotationParameterList)psiElement).getAttributes();
        int offset;
        if (params.length == 0) {
          offset = psiElement.getTextRange().getStartOffset() + 1;
        }
        else {
          offset = params[params.length - 1].getTextRange().getEndOffset();
        }
        editor.getDocument().insertString(offset, ")");
      }
    }
  }
}
