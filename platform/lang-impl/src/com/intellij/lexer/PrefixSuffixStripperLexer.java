// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author max
 */
package com.intellij.lexer;

import com.intellij.psi.tree.IElementType;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;

public final class PrefixSuffixStripperLexer extends LexerBase {
  private CharSequence myBuffer;
  private char[] myBufferArray;
  private int myTokenStart;
  private int myTokenEnd;
  private IElementType myTokenType;
  private int myState;
  private int myBufferEnd;
  private final String myPrefix;
  private final IElementType myPrefixType;
  private final String mySuffix;
  private final IElementType myMiddleTokenType;
  private final IElementType mySuffixType;

  public PrefixSuffixStripperLexer(final String prefix,
                                   final IElementType prefixType,
                                   final String suffix,
                                   final IElementType suffixType,
                                   final IElementType middleTokenType) {
    mySuffixType = suffixType;
    myMiddleTokenType = middleTokenType;
    mySuffix = suffix;
    myPrefixType = prefixType;
    myPrefix = prefix;
  }

  @Override
  public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
    myBuffer = buffer;
    myBufferArray = CharArrayUtil.fromSequenceWithoutCopying(buffer);
    myTokenStart = startOffset;
    myTokenEnd = startOffset;
    myTokenType = null;
    myState = initialState;
    myBufferEnd = endOffset;
  }

  @Override
  public IElementType getTokenType() {
    locateToken();
    return myTokenType;
  }

  @Override
  public int getTokenStart() {
    locateToken();
    return myTokenStart;
  }

  @Override
  public int getTokenEnd() {
    locateToken();
    return myTokenEnd;
  }

  @Override
  public int getState() {
    return myState;
  }

  @Override
  public int getBufferEnd() {
    return myBufferEnd;
  }

  @Override
  public @NotNull CharSequence getBufferSequence() {
    return myBuffer;
  }

  @Override
  public void advance() {
    myTokenType = null;
  }

  private void locateToken() {
    if (myTokenType != null || myState == 3) return;

    if (myState == 0) {
      myTokenEnd = myTokenStart + myPrefix.length();
      myTokenType = myPrefixType;
      myState = myTokenEnd < myBufferEnd ? 1 : 3;
      return;
    }

    if (myState == 1) {
      myTokenStart = myTokenEnd;
      final int suffixStart = myBufferEnd - mySuffix.length();
      myTokenType = myMiddleTokenType;
      if ( (myBufferArray != null && CharArrayUtil.regionMatches(myBufferArray, suffixStart, myBufferEnd, mySuffix)) ||
           (myBufferArray == null && CharArrayUtil.regionMatches(myBuffer, suffixStart, myBufferEnd, mySuffix))
         ) {
        myTokenEnd = suffixStart;
        if (myTokenStart < myTokenEnd) {
          myState = 2;
        }
        else {
          myState = 3;
          myTokenType = mySuffixType;
          myTokenEnd = myBufferEnd;
        }
      }
      else {
        myTokenEnd = myBufferEnd;
        myState = 3;
      }

      return;
    }

    if (myState == 2) {
      myTokenStart = myTokenEnd;
      myTokenEnd = myBufferEnd;
      myTokenType = mySuffixType;
      myState = 3;
    }
  }
}
