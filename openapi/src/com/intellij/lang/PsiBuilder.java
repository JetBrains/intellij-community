package com.intellij.lang;

import com.intellij.psi.tree.IElementType;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 17, 2005
 * Time: 1:27:50 PM
 * To change this template use File | Settings | File Templates.
 */
public interface PsiBuilder {
  CharSequence getOriginalText();

  IElementType getTokenType();
  void advanceLexer();
  int getCurrentOffset();

  interface Marker {
    Marker preceed();
    void drop();
    void rollbackTo();
    void done(IElementType type);
  }

  Marker mark();

  void error(String messageText);

  boolean eof();

  ASTNode getTreeBuilt();
}
