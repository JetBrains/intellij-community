package com.intellij.codeInsight.editorActions.fillParagraph;

import com.intellij.lang.LanguageExtension;

/**
 * User : ktisha
 */
public class LanguageFillParagraphExtension extends LanguageExtension<ParagraphFillHandler> {

  public static final String EP_NAME = "com.intellij.codeInsight.fillParagraph";
  public static final LanguageFillParagraphExtension INSTANCE = new LanguageFillParagraphExtension();

  public LanguageFillParagraphExtension() {
    super(EP_NAME, new ParagraphFillHandler());
  }
}
