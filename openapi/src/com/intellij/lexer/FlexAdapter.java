package com.intellij.lexer;

import com.intellij.psi.tree.IElementType;
import com.intellij.util.text.CharSequenceReader;

import java.io.IOException;
import java.io.Reader;

/**
 * @author max
 */
public abstract class FlexAdapter /*implements Lexer*/ {
  private FlexLexer myFlex = null;
  private IElementType myTokenType = null;
  private CharSequence myText;

  public void start(CharSequence text) {
    myText = text;
    Reader reader = new CharSequenceReader(text);
    if (myFlex == null) {
      myFlex = createFlexLexer(reader);
    }
    else {
      myFlex.yyreset(reader);
    }
  }

  public void reset(int offset, int state) {
    myFlex.yyreset(new CharSequenceReader(myText.subSequence(offset, myText.length())));
    myFlex.yybegin(state);
  }

  protected abstract FlexLexer createFlexLexer(final Reader reader);

  public CharSequence getText() {
    return myText;
  }

  public int getState() {
    return (short)myFlex.yystate();
  }

  public int getTokenStart() {
    locateToken();
    return myFlex.getTokenStart();
  }

  public int getTokenEnd() {
    locateToken();
    return myFlex.getTokenEnd();
  }

  public IElementType getTokenType() {
    locateToken();
    return myTokenType;
  }

  public void advance() {
    locateToken();
    myTokenType = null;
  }

  private void locateToken() {
    if (myTokenType != null) return;
    try {
      myTokenType = myFlex.advance();
    }
    catch (IOException e) { /* Can't happen */ }
  }
}
