/*
 * @author max
 */
package com.intellij.lexer;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Nullable;

public class DelegateLexer extends LexerBase {
  private final Lexer myDelegate;

  public DelegateLexer(Lexer delegate) {
    myDelegate = delegate;
  }

  public Lexer getDelegate() {
    return myDelegate;
  }

  public void start(CharSequence buffer, int startOffset, int endOffset, int initialState) {
    myDelegate.start(buffer, startOffset, endOffset, initialState);
  }

  public int getState() {
    return myDelegate.getState();
  }

  @Nullable
  public IElementType getTokenType() {
    return myDelegate.getTokenType();
  }

  public int getTokenStart() {
    return myDelegate.getTokenStart();
  }

  public int getTokenEnd() {
    return myDelegate.getTokenEnd();
  }

  public void advance() {
    myDelegate.advance();
  }

  public CharSequence getBufferSequence() {
    return myDelegate.getBufferSequence();
  }

  public int getBufferEnd() {
    return myDelegate.getBufferEnd();
  }
}
