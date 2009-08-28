package com.intellij.psi.impl.source.parsing;

import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.lexer.Lexer;
import com.intellij.lexer.LexerUtil;
import com.intellij.util.CharTable;
import com.intellij.lang.ASTFactory;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: 20.04.2009
 * Time: 15:02:52
 * To change this template use File | Settings | File Templates.
 */
public class ParseUtilBase {
  public static TreeElement createTokenElement(Lexer lexer, CharTable table) {
    IElementType tokenType = lexer.getTokenType();
    if (tokenType == null) return null;

    return ASTFactory.leaf(tokenType, LexerUtil.internToken(lexer, table));
  }
}
