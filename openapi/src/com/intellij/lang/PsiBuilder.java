package com.intellij.lang;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.PsiElement;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 17, 2005
 * Time: 1:27:50 PM
 * To change this template use File | Settings | File Templates.
 */
public interface PsiBuilder {
  interface Marker {
  }

  interface Lexem {
    IElementType getTokenType();

    String getTokenText();
  }

  void advanceLexer();

  Lexem getCurrentLexem();

  Marker start(IElementType symbol);

  void rollbackTo(Marker marker);

  void drop(Marker marker);

  void done(Marker marker);

  void insertErrorElement(String messageText);

  boolean eof();

  ASTNode getTreeBuilt();
}
