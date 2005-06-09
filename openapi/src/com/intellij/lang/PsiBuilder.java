package com.intellij.lang;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 17, 2005
 * Time: 1:27:50 PM
 * To change this template use File | Settings | File Templates.
 */
public interface PsiBuilder {
  CharSequence getOriginalText();

  void advanceLexer();

  @Nullable(documentation = "Returns null when lexing is over")
  IElementType getTokenType();

  @Nullable(documentation = "Returns null when lexing is over")
  String getTokenText();

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

  /**
   * Builder will print stack trace to marker allocation position if one is not done when calling getTreeBuilt().
   * @param dbgMode
   */
  void setDebugMode(boolean dbgMode);
}
