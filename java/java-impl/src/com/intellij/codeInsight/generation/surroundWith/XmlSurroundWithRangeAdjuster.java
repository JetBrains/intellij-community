// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.generation.surroundWith;

import com.intellij.lang.Language;
import com.intellij.lang.StdLanguages;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.lang.xhtml.XHTMLLanguage;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.jsp.JspLanguage;
import com.intellij.psi.jsp.JspxLanguage;
import com.intellij.psi.xml.XmlFile;


public final class XmlSurroundWithRangeAdjuster implements SurroundWithRangeAdjuster {
  private static boolean isLanguageWithWSSignificant(Language lang) {
    return lang == HTMLLanguage.INSTANCE ||
           lang == XHTMLLanguage.INSTANCE ||
           lang instanceof JspLanguage ||
           lang instanceof JspxLanguage;
  }

  private static Language getLanguage(PsiElement element) {
    Language lang = element.getLanguage();
    if (lang == XMLLanguage.INSTANCE) {
      PsiElement parent = element.getParent();
      lang = parent.getLanguage();
    }
    return lang;
  }

  @Override
  public TextRange adjustSurroundWithRange(final PsiFile file, final TextRange selectedRange) {
    if (!(file instanceof XmlFile)) return selectedRange;
    int startOffset = selectedRange.getStartOffset();
    int endOffset = selectedRange.getEndOffset();
    PsiElement element1 = file.findElementAt(startOffset);
    PsiElement element2 = file.findElementAt(endOffset - 1);

    Language lang1 = getLanguage(element1);
    Language lang2 = getLanguage(element2);

    if (element1 instanceof PsiWhiteSpace && isLanguageWithWSSignificant(lang1) ) {
      startOffset = element1.getTextRange().getEndOffset();
      element1 = file.findElementAt(startOffset);
    }
    if (element2 instanceof PsiWhiteSpace && isLanguageWithWSSignificant(lang2) ) {
      endOffset = element2.getTextRange().getStartOffset();
      element2 = file.findElementAt(endOffset);
    }

    lang1 = getLanguage(element1);
    lang2 = getLanguage(element2);

    if(lang1 != lang2) return null;

    TextRange.assertProperRange(startOffset, endOffset, "Wrong offsets for " + selectedRange.substring(file.getText()));
    return new TextRange(startOffset, endOffset);
  }
}
