package com.intellij.lang;

import com.intellij.codeFormatting.PseudoTextBuilder;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.PlainSyntaxHighlighter;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 16, 2005
 * Time: 9:10:05 PM
 * To change this template use File | Settings | File Templates.
 */
public abstract class Language {
  public SyntaxHighlighter getSyntaxHighlighter() {
    return new PlainSyntaxHighlighter();
  }

  public PseudoTextBuilder getFormatter() {
    return null;
  }

}
