package com.intellij.lang;

import com.intellij.codeFormatting.PseudoTextBuilder;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.PlainSyntaxHighlighter;
import com.intellij.lexer.Lexer;
import com.intellij.lexer.EmptyLexer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;

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

  public ParserDefinition getParserDefinition() {
    return null;
  }


}
