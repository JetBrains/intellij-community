package com.intellij.lexer;

import com.intellij.psi.tree.IElementType;
import com.intellij.util.text.CharArrayCharSequence;

import java.io.IOException;

/**
 * @author max
 */
public abstract class FlexAdapter implements Lexer {
  private FlexLexer myFlex = null;
  private IElementType myTokenType = null;
  private CharArrayCharSequence myText;
  private int myStart;
  private int myEnd;
  private char[] myBuffer;

  protected FlexAdapter(final FlexLexer flex) {
    myFlex = flex;
  }

  public FlexLexer getFlex() {
    return myFlex;
  }

  public void start(char[] buffer) {
    start(buffer, 0, buffer.length, 0);
  }

  public void start(char[] buffer, int startOffset, int endOffset) {
    start(buffer, startOffset, endOffset, 0);
  }

  public void start(char[] buffer, int startOffset, int endOffset, int initialState) {
    myBuffer = buffer;
    myText = new CharArrayCharSequence(myBuffer, startOffset, endOffset);
    myStart = startOffset;
    myEnd = endOffset;
    myFlex.reset(myText, initialState);
    myTokenType = null;
  }

  public int getState() {
    return myFlex.yystate();
  }

  public int getLastState() {
    return 0;
  }

  public IElementType getTokenType() {
    locateToken();
    return myTokenType;
  }

  public int getTokenStart() {
    locateToken();
    return myFlex.getTokenStart() + myStart;
  }

  public int getTokenEnd() {
    locateToken();
    return myFlex.getTokenEnd() + myStart;
  }

  public void advance() {
    locateToken();
    myTokenType = null;
  }

  public char[] getBuffer() {
    return myBuffer;
  }

  public int getBufferEnd() {
    return myEnd;
  }

  public int getSmartUpdateShift() {
    return 10;
  }

  public Object clone() {
    return null;
  }

  private void locateToken() {
    if (myTokenType != null) return;
    try {
      myTokenType = myFlex.advance();
    }
    catch (IOException e) { /*Can't happen*/ }
  }
}
