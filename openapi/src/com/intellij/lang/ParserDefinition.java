package com.intellij.lang;

import com.intellij.lexer.Lexer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 17, 2005
 * Time: 7:57:54 PM
 * To change this template use File | Settings | File Templates.
 */
public interface ParserDefinition {
  Lexer createLexer();
  PsiParser createParser();
  PsiElement createElement(IElementType type, ElementFactoryHelper helper);
}
