/*
 * @author max
 */
package com.intellij.psi.templateLanguages;

import com.intellij.lexer.Lexer;
import com.intellij.lexer.LexerPosition;
import com.intellij.lexer.LexerState;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Nullable;

public class TemplateBlackAndWhiteLexer implements Lexer{
  private final Lexer myBaseLexer;
  private final Lexer myTemplateLexer;
  private final IElementType myTemplateElementType;
  private final IElementType myOuterElementType;
  private int myTemplateState = 0;

  public TemplateBlackAndWhiteLexer(Lexer baseLexer, Lexer templateLexer, IElementType templateElementType, IElementType outerElementType) {
    myTemplateLexer = templateLexer;
    myBaseLexer = baseLexer;
    myTemplateElementType = templateElementType;
    myOuterElementType = outerElementType;
  }

  public void start(char[] buffer) {
    start(buffer, 0, buffer.length, 0);
  }

  public void start(char[] buffer, int startOffset, int endOffset) {
    start(buffer, startOffset, endOffset, 0);
  }

  public void start(char[] buffer, int startOffset, int endOffset, int initialState) {
    myBaseLexer.start(buffer, startOffset, endOffset, initialState);
    setupTemplateToken();
  }

  public void start(final CharSequence buffer, final int startOffset, final int endOffset, final int initialState) {
    myBaseLexer.start(buffer, startOffset, endOffset, initialState);
    setupTemplateToken();
  }

  public CharSequence getBufferSequence() {
    return myBaseLexer.getBufferSequence();
  }

  public int getState() {
    return myBaseLexer.getState();
  }

  @Nullable
  public IElementType getTokenType() {
    IElementType tokenType = myBaseLexer.getTokenType();
    if (tokenType == null) return null;

    return tokenType == myTemplateElementType ? myTemplateElementType : myOuterElementType;
  }

  public int getTokenStart() {
    IElementType tokenType = myBaseLexer.getTokenType();
    if (tokenType == myTemplateElementType) {
      return myTemplateLexer.getTokenStart();
    }
    else {
      return myBaseLexer.getTokenStart();
    }
  }

  public int getTokenEnd() {
    IElementType tokenType = myBaseLexer.getTokenType();
    if (tokenType == myTemplateElementType) {
      return myTemplateLexer.getTokenEnd();
    }
    else {
      return myBaseLexer.getTokenEnd();
    }
  }

  public void advance() {
    IElementType tokenType = myBaseLexer.getTokenType();
    if (tokenType == myTemplateElementType) {
      myTemplateLexer.advance();
      myTemplateState = myTemplateLexer.getState();
      if (myTemplateLexer.getTokenType() != null) return;
    }
    myBaseLexer.advance();
    setupTemplateToken();
  }

  private void setupTemplateToken() {
    while (true) {
      IElementType tokenType = myBaseLexer.getTokenType();
      if (tokenType != myTemplateElementType) {
        return;
      }

      myTemplateLexer.start(myBaseLexer.getBufferSequence(), myBaseLexer.getTokenStart(), myBaseLexer.getTokenEnd(), myTemplateState);
      if (myTemplateLexer.getTokenType() != null) return;
      myBaseLexer.advance();
    }
  }


  private static class Position implements LexerPosition {
    private final LexerPosition myTemplatePosition;
    private final LexerPosition myBasePosition;

    public Position(LexerPosition templatePosition, LexerPosition jspPosition) {
      myTemplatePosition = templatePosition;
      myBasePosition = jspPosition;
    }

    public int getOffset() {
      return Math.max(myBasePosition.getOffset(), myTemplatePosition.getOffset());
    }

    public LexerPosition getTemplatePosition() {
      return myTemplatePosition;
    }

    public LexerPosition getBasePosition() {
      return myBasePosition;
    }

    public LexerState getState() {
      return null;
    }
  }

  public LexerPosition getCurrentPosition() {
    return new Position(myTemplateLexer.getCurrentPosition(), myBaseLexer.getCurrentPosition());
  }

  public void restore(LexerPosition position) {
    final Position p = (Position)position;
    myBaseLexer.restore(p.getBasePosition());
    final LexerPosition templatePos = p.getTemplatePosition();
    if (templatePos != null && templatePos.getOffset() < myTemplateLexer.getBufferEnd()) {
      myTemplateLexer.restore(templatePos);
    }
    else {
      setupTemplateToken();
    }
  }

  public char[] getBuffer() {
    return myBaseLexer.getBuffer();
  }

  public int getBufferEnd() {
    return myBaseLexer.getBufferEnd();
  }

}