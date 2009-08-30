package com.intellij.codeInsight.generation.surroundWith;

import com.intellij.lang.Language;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.PsiElement;

/**
 * @author yole
 */
public class XmlSurroundWithRangeAdjuster implements SurroundWithRangeAdjuster {
  private static boolean isLanguageWithWSSignificant(Language lang) {
    return lang == StdLanguages.HTML ||
           lang == StdLanguages.XHTML ||
           lang == StdLanguages.JSP ||
           lang == StdLanguages.JSPX;
  }

  private static Language getLanguage(PsiElement element) {
    Language lang = element.getLanguage();
    if (lang == StdLanguages.XML) {
      PsiElement parent = element.getParent();
      lang = parent.getLanguage();
    }
    return lang;
  }

  public TextRange adjustSurroundWithRange(final PsiFile file, final TextRange selectedRange) {
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
    return new TextRange(startOffset, endOffset);
  }
}
