// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.lookup;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.ModNavigatorTailType;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.ModNavigator;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettingsFacade;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;

public class CommaTailType extends ModNavigatorTailType {
  public static final ModNavigatorTailType INSTANCE = new CommaTailType();

  @Override
  public int processTail(@NotNull ModNavigator navigator, int tailOffset) {
    PsiFile psiFile = navigator.getPsiFile();
    Language language = PsiUtilCore.getLanguageAtOffset(psiFile, tailOffset);
    CodeStyleSettingsFacade codeStyleFacade = CodeStyle.getFacade(psiFile).withLanguage(language);
    if (codeStyleFacade.isSpaceBeforeComma()) tailOffset = insertChar(navigator, tailOffset, ' ');
    tailOffset = insertChar(navigator, tailOffset, ',');
    if (codeStyleFacade.isSpaceAfterComma()) tailOffset = insertChar(navigator, tailOffset, ' ');
    return tailOffset;
  }

  @Override
  public String toString() {
    return "COMMA";
  }
}
