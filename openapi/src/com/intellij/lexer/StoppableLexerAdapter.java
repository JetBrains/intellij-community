/*
 * @author max
 */
package com.intellij.lexer;

import com.intellij.psi.tree.IElementType;

public class StoppableLexerAdapter implements Lexer {

  public static interface StoppingCondition {
    boolean stopsAt(IElementType token, int start, int end);
  }

  private Lexer myOriginal;
  private StoppingCondition myCondition;
  private boolean myStopped = false;

  public Lexer getOriginal() {
    return myOriginal;
  }

  public StoppableLexerAdapter(final StoppingCondition condition, final Lexer original) {
    myCondition = condition;
    myOriginal = original;
    myStopped = myCondition.stopsAt(myOriginal.getTokenType(), myOriginal.getTokenStart(), myOriginal.getTokenEnd());
  }

  public void advance() {
    if (myStopped) return;
    myOriginal.advance();

    if (myCondition.stopsAt(myOriginal.getTokenType(), myOriginal.getTokenStart(), myOriginal.getTokenEnd())) {
      myStopped = true;
    }
  }

  public int getPrevTokenEnd() {
    return myOriginal instanceof StoppableLexerAdapter ? ((StoppableLexerAdapter)myOriginal).getPrevTokenEnd() : ((FilterLexer)myOriginal).getPrevTokenEnd();
  }

  public char[] getBuffer() {
    return myOriginal.getBuffer();
  }

  public int getBufferEnd() {
    return myOriginal.getBufferEnd();
  }

  public LexerPosition getCurrentPosition() {
    return myOriginal.getCurrentPosition();
  }

  public int getState() {
    return myOriginal.getState();
  }

  public int getTokenEnd() {
    return myStopped ? myOriginal.getTokenStart() : myOriginal.getTokenEnd();
  }

  public int getTokenStart() {
    return myOriginal.getTokenStart();
  }

  public IElementType getTokenType() {
    return myStopped ? null : myOriginal.getTokenType();
  }

  public void restore(LexerPosition position) {
    myOriginal.restore(position);
  }

  public void start(char[] buffer) {
    myOriginal.start(buffer);
  }

  public void start(char[] buffer, int startOffset, int endOffset) {
    myOriginal.start(buffer, startOffset, endOffset);
  }

  public void start(char[] buffer, int startOffset, int endOffset, int initialState) {
    myOriginal.start(buffer, startOffset, endOffset, initialState);
  }

  public void start(final CharSequence buffer, final int startOffset, final int endOffset, final int initialState) {
    myOriginal.start(buffer, startOffset, endOffset, initialState);
  }

  public CharSequence getBufferSequence() {
    return myOriginal.getBufferSequence();
  }
}