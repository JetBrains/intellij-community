// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.lookup;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.ModNavigatorTailType;
import com.intellij.codeInsight.TailType;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ModNavigator;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettingsFacade;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;

public class EqTailType extends ModNavigatorTailType {
  public static final TailType INSTANCE = new EqTailType();

  protected boolean isSpaceAroundAssignmentOperators(@NotNull PsiFile psiFile, int tailOffset) {
    Language language = PsiUtilCore.getLanguageAtOffset(psiFile, tailOffset);
    CodeStyleSettingsFacade codeStyleFacade = CodeStyle.getFacade(psiFile).withLanguage(language);
    return codeStyleFacade.isSpaceAroundAssignmentOperators();
  }

  @Override
  public int processTail(@NotNull ModNavigator navigator, int tailOffset) {
    Document document = navigator.getDocument();
    int textLength = document.getTextLength();
    CharSequence chars = document.getCharsSequence();
    if (tailOffset < textLength - 1 && chars.charAt(tailOffset) == ' ' && chars.charAt(tailOffset + 1) == '=') {
      return moveCaret(navigator, tailOffset, 2);
    }
    if (tailOffset < textLength && chars.charAt(tailOffset) == '=') {
      return moveCaret(navigator, tailOffset, 1);
    }
    if (isSpaceAroundAssignmentOperators(navigator.getPsiFile(), tailOffset)) {
      document.insertString(tailOffset, " =");
      tailOffset = moveCaret(navigator, tailOffset, 2);
      tailOffset = insertChar(navigator, tailOffset, ' ');
    }
    else {
      document.insertString(tailOffset, "=");
      tailOffset = moveCaret(navigator, tailOffset, 1);
    }
    return tailOffset;
  }
}
