package com.intellij.lexer;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;

/**
 *
 */
public class FilterLexer implements Lexer {
  private Lexer myOriginal;
  private Filter myFilter;
  private boolean[] myStateFilter;
  private int myPrevTokenEnd;

  public interface Filter {
    public boolean reject(IElementType type);
  }

  public static final class SetFilter implements Filter {
    private TokenSet mySet;

    public SetFilter(TokenSet set) {
      mySet = set;
    }

    public boolean reject(IElementType type) {
      return mySet.isInSet(type);
    }
  }

  public FilterLexer(Lexer original, Filter filter, boolean[] stateFilter) {
    myOriginal = original;
    myFilter = filter;
    myStateFilter = stateFilter;
  }

  public FilterLexer(Lexer original, Filter filter) {
    this(original, filter, null);
  }

  public Object clone() {
    try{
      FilterLexer clone = (FilterLexer)super.clone();
      clone.myOriginal = (Lexer)myOriginal.clone();
      return clone;
    }
    catch(CloneNotSupportedException e){
      return null;
    }
  }

  public Lexer getOriginal() {
    return myOriginal;
  }

  public void start(char[] buffer) {
    myOriginal.start(buffer);
    myPrevTokenEnd = -1;
    locateToken();
  }

  public void start(char[] buffer, int startOffset, int endOffset) {
    myOriginal.start(buffer, startOffset, endOffset);
    myPrevTokenEnd = -1;
    locateToken();
  }

  public void start(char[] buffer, int startOffset, int endOffset, int initialState) {
    myOriginal.start(buffer, startOffset, endOffset, initialState);
    myPrevTokenEnd = -1;
    locateToken();
  }

  public int getState() {
    return myOriginal.getState();
  }

  public int getLastState() {
    return myOriginal.getLastState();
  }

  public final IElementType getTokenType() {
    return myOriginal.getTokenType();
  }

  public int getTokenStart() {
    return myOriginal.getTokenStart();
  }

  public int getTokenEnd() {
    return myOriginal.getTokenEnd();
  }

  public char[] getBuffer() {
    return myOriginal.getBuffer();
  }

  public int getBufferEnd() {
    return myOriginal.getBufferEnd();
  }

  public void advance() {
    myPrevTokenEnd = myOriginal.getTokenEnd();
    myOriginal.advance();
    locateToken();
  }

  public int getPrevTokenEnd() {
    return myPrevTokenEnd;
  }

  public int getSmartUpdateShift() {
    return myOriginal.getSmartUpdateShift();
  }

  public final void locateToken(){
    while(true){
      IElementType tokenType = myOriginal.getTokenType();
      if (tokenType == null) break;
      if (myFilter == null || !myFilter.reject(tokenType)) {
        if (myStateFilter == null || !myStateFilter[myOriginal.getState()]){
          break;
        }
      }
      myOriginal.advance();
    }
  }
}

