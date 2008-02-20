package com.intellij.lang;

import com.intellij.psi.PsiElement;

/**
 * @author yole
 */
public class LanguageLiteralEscapers extends LanguageExtension<LiteralEscaper> {
  public static final LanguageLiteralEscapers INSTANCE = new LanguageLiteralEscapers();

  private LanguageLiteralEscapers() {
    super("com.intellij.lang.literalEscaper", new LiteralEscaper() {
      public String getEscapedText(final PsiElement context, final String originalText) {
        return originalText;
      }
    });
  }
}
