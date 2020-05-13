// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.lookup;

import com.intellij.codeInsight.TailType;
import com.intellij.codeStyle.CodeStyleFacade;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiEditorUtil;
import com.intellij.psi.util.PsiUtilCore;

public class CommaTailType extends TailType {
  public static final TailType INSTANCE = new CommaTailType();

  @Override
  public int processTail(final Editor editor, int tailOffset) {
    CodeStyleFacade codeStyleFacade = CodeStyleFacade.getInstance(editor.getProject());
    PsiFile psiFile = PsiEditorUtil.getPsiFile(editor);
    Language language = PsiUtilCore.getLanguageAtOffset(PsiEditorUtil.getPsiFile(editor), tailOffset);
    if (codeStyleFacade.useSpaceBeforeComma(psiFile, language)) tailOffset = insertChar(editor, tailOffset, ' ');
    tailOffset = insertChar(editor, tailOffset, ',');
    if (codeStyleFacade.useSpaceAfterComma(psiFile, language)) tailOffset = insertChar(editor, tailOffset, ' ');
    return tailOffset;
  }

  public String toString() {
    return "COMMA";
  }
}
