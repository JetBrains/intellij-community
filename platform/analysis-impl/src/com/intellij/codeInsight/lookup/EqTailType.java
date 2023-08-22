// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.lookup;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.TailType;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettingsFacade;
import com.intellij.psi.util.PsiEditorUtil;
import com.intellij.psi.util.PsiUtilCore;

public class EqTailType extends TailType {
  public static final TailType INSTANCE = new EqTailType();

  protected boolean isSpaceAroundAssignmentOperators(Editor editor, int tailOffset) {
    PsiFile psiFile = PsiEditorUtil.getPsiFile(editor);
    Language language = PsiUtilCore.getLanguageAtOffset(psiFile, tailOffset);
    CodeStyleSettingsFacade codeStyleFacade = CodeStyle.getFacade(psiFile).withLanguage(language);
    return codeStyleFacade.isSpaceAroundAssignmentOperators();
  }

  @Override
  public int processTail(final Editor editor, int tailOffset) {
    Document document = editor.getDocument();
    int textLength = document.getTextLength();
    CharSequence chars = document.getCharsSequence();
    if (tailOffset < textLength - 1 && chars.charAt(tailOffset) == ' ' && chars.charAt(tailOffset + 1) == '=') {
      return moveCaret(editor, tailOffset, 2);
    }
    if (tailOffset < textLength && chars.charAt(tailOffset) == '=') {
      return moveCaret(editor, tailOffset, 1);
    }
    if (isSpaceAroundAssignmentOperators(editor, tailOffset)) {
      document.insertString(tailOffset, " =");
      tailOffset = moveCaret(editor, tailOffset, 2);
      tailOffset = insertChar(editor, tailOffset, ' ');
    }
    else {
      document.insertString(tailOffset, "=");
      tailOffset = moveCaret(editor, tailOffset, 1);
    }
    return tailOffset;
  }
}
