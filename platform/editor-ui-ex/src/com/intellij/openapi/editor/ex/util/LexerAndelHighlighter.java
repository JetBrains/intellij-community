/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.editor.ex.util;

import com.intellij.lang.Language;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

public class LexerAndelHighlighter {
  private LexerAndelHighlighter() {}

  public static class TokensContainer {
    private final Lexer myLexer;
    private final int myInitialState;
    private final SegmentArrayWithData mySegments;
    private final SyntaxHighlighter myHighlighter;
    private static final EditorColorsScheme colorScheme = EditorColorsManager.getInstance().getScheme("zenburn");
    private final String myLanguageID;

    private TokensContainer(SyntaxHighlighter highlighter, String languageID) {
      mySegments = new SegmentArrayWithData();
      myHighlighter = highlighter;
      myLanguageID = languageID;
      myLexer = highlighter.getHighlightingLexer();
      myLexer.start(ArrayUtil.EMPTY_CHAR_SEQUENCE);
      myInitialState = myLexer.getState();
    }

    private TokensContainer(SyntaxHighlighter highlighter, String languageID, SegmentArrayWithData se) {
      myHighlighter = highlighter;
      myLanguageID = languageID;
      myLexer = highlighter.getHighlightingLexer();
      myLexer.start(ArrayUtil.EMPTY_CHAR_SEQUENCE);
      myInitialState = myLexer.getState();
      mySegments = se;
    }

    public int getTokenIndexByOffset(int offset) {
      final int latestValidOffset = mySegments.getLastValidOffset();
      return mySegments.findSegmentIndex(offset <= latestValidOffset ? offset : latestValidOffset);
    }

    public int getTokensCount() {
      return mySegments.getSegmentCount();
    }

    public int getStart(int index) {
      return mySegments.getSegmentStart(index);
    }

    public int getEnd(int index) {
      return mySegments.getSegmentEnd(index);
    }

    public IElementType getTokenType(int index) {
      return unpackToken(mySegments.getSegmentData(index));
    }

    public int getTokenCount() {
      return mySegments.getSegmentCount();
    }

    public TextAttributes getTextAttributes(int index) {
      // TODO cache text attributes
      final TextAttributesKey[] keys = myHighlighter.getTokenHighlights(getTokenType(index));
      TextAttributes attrs = new TextAttributes();
      for (TextAttributesKey key : keys) {
        TextAttributes attrs2 = colorScheme.getAttributes(key);
        if (attrs2 != null) {
          attrs = TextAttributes.merge(attrs, attrs2);
        }
      }
      return attrs;
    }


  }

  private static int packData(final IElementType tokenType, final int initialState, final int state) {
    final short idx = tokenType.getIndex();
    return state == initialState ? idx : -idx;
  }

  protected static IElementType unpackToken(int data) {
    return IElementType.find((short)Math.abs(data));
  }

  @NotNull
  public static TokensContainer createTokensContainer(@NotNull String languageID, @NotNull CharSequence text) {
    final Language lang = Language.findLanguageByID(languageID);
    final TokensContainer tc = new TokensContainer(SyntaxHighlighterFactory.getSyntaxHighlighter(lang, null, null), languageID);
    final int textLength = text.length();
    tc.myLexer.start(text, 0, textLength, tc.myInitialState);
    tc.mySegments.removeAll();
    int i = 0;
    while (true) {
      final IElementType tokenType = tc.myLexer.getTokenType();
      if (tokenType == null) break;

      int data = packData(tokenType, tc.myInitialState, tc.myLexer.getState());
      tc.mySegments.setElementAt(i, tc.myLexer.getTokenStart(), tc.myLexer.getTokenEnd(), data);
      i++;
      tc.myLexer.advance();
    }

    if (textLength > 0 && (tc.mySegments.mySegmentCount == 0 || tc.mySegments.myEnds[tc.mySegments.mySegmentCount - 1] != textLength)) {
      throw new IllegalStateException("Unexpected termination offset for lexer " + tc.myLexer);
    }

    return tc;
  }

  private static boolean isInitialState(int data) {
    return data >= 0;
  }

  private static boolean segmentsEqual(SegmentArrayWithData a1, int idx1, SegmentArrayWithData a2, int idx2, final int offsetShift) {
    return a1.getSegmentStart(idx1) + offsetShift == a2.getSegmentStart(idx2) &&
           a1.getSegmentEnd(idx1) + offsetShift == a2.getSegmentEnd(idx2) &&
           a1.getSegmentData(idx1) == a2.getSegmentData(idx2);
  }

  public static class TextChange {
    private final CharSequence myText;
    private final int myOffset;
    private final int myNewLength;
    private final int myOldLength;

    public TextChange(CharSequence text, int offset, int newLength, int oldLength) {
      myText = text;
      myOffset = offset;
      myNewLength = newLength;
      myOldLength = oldLength;
    }
  }

  @NotNull
  public static TokensContainer changeText(@NotNull TokensContainer tokensContainer, @NotNull TextChange e) {
    if(tokensContainer.mySegments.getSegmentCount() == 0) {
      return createTokensContainer(tokensContainer.myLanguageID, e.myText);
    }
    int oldStartOffset = e.myOffset;

    final int segmentIndex = tokensContainer.mySegments.findSegmentIndex(oldStartOffset) - 2;
    final int oldStartIndex = Math.max(0, segmentIndex);
    int startIndex = oldStartIndex;

    int data;
    do {
      data = tokensContainer.mySegments.getSegmentData(startIndex);
      if (isInitialState(data)|| startIndex == 0) break;
      startIndex--;
    }
    while (true);

    int startOffset = tokensContainer.mySegments.getSegmentStart(startIndex);
    int newEndOffset = e.myOffset + e.myNewLength;

    tokensContainer.myLexer.start(e.myText, startOffset, e.myText.length(), tokensContainer.myInitialState);

    int lastTokenStart = -1;
    int lastLexerState = -1;
    IElementType lastTokenType = null;

    while (tokensContainer.myLexer.getTokenType() != null) {
      if (startIndex >= oldStartIndex) break;

      int tokenStart = tokensContainer.myLexer.getTokenStart();
      int lexerState = tokensContainer.myLexer.getState();

      if (tokenStart == lastTokenStart && lexerState == lastLexerState && tokensContainer.myLexer.getTokenType() == lastTokenType) {
        throw new IllegalStateException("Lexer is not progressing after calling advance()");
      }

      int tokenEnd = tokensContainer.myLexer.getTokenEnd();
      data = packData(tokensContainer.myLexer.getTokenType(), tokensContainer.myInitialState, lexerState);
      if (tokensContainer.mySegments.getSegmentStart(startIndex) != tokenStart ||
          tokensContainer.mySegments.getSegmentEnd(startIndex) != tokenEnd ||
          tokensContainer.mySegments.getSegmentData(startIndex) != data) {
        break;
      }
      startIndex++;
      lastTokenType = tokensContainer.myLexer.getTokenType();
      tokensContainer.myLexer.advance();
      lastTokenStart = tokenStart;
      lastLexerState = lexerState;
    }

    startOffset = tokensContainer.mySegments.getSegmentStart(startIndex);
    int repaintEnd = -1;
    int insertSegmentCount = 0;
    int oldEndIndex = -1;
    lastTokenType = null;
    SegmentArrayWithData insertSegments = new SegmentArrayWithData();

    while(tokensContainer.myLexer.getTokenType() != null) {
      int tokenStart = tokensContainer.myLexer.getTokenStart();
      int lexerState = tokensContainer.myLexer.getState();

      if (tokenStart == lastTokenStart && lexerState == lastLexerState && tokensContainer.myLexer.getTokenType() == lastTokenType) {
        throw new IllegalStateException("Lexer is not progressing after calling advance()");
      }

      lastTokenStart = tokenStart;
      lastLexerState = lexerState;
      lastTokenType = tokensContainer.myLexer.getTokenType();

      int tokenEnd = tokensContainer.myLexer.getTokenEnd();
      data = packData(tokensContainer.myLexer.getTokenType(), tokensContainer.myInitialState, lexerState);
      if(tokenStart >= newEndOffset && lexerState == tokensContainer.myInitialState) {
        int shiftedTokenStart = tokenStart - e.myNewLength + e.myOldLength;
        int index = tokensContainer.mySegments.findSegmentIndex(shiftedTokenStart);
        if (tokensContainer.mySegments.getSegmentStart(index) == shiftedTokenStart && tokensContainer.mySegments.getSegmentData(index) == data) {
          repaintEnd = tokenStart;
          oldEndIndex = index;
          break;
        }
      }
      insertSegments.setElementAt(insertSegmentCount, tokenStart, tokenEnd, data);
      insertSegmentCount++;
      tokensContainer.myLexer.advance();
    }

    final int shift = e.myNewLength - e.myOldLength;
    if (repaintEnd > 0) {
      while (insertSegmentCount > 0 && oldEndIndex > startIndex) {
        if (!segmentsEqual(tokensContainer.mySegments, oldEndIndex - 1, insertSegments, insertSegmentCount - 1, shift)) {
          break;
        }
        insertSegmentCount--;
        oldEndIndex--;
        insertSegments.remove(insertSegmentCount, insertSegmentCount + 1);
      }
    }

    if (oldEndIndex < 0) {
      oldEndIndex = tokensContainer.mySegments.getSegmentCount();
    }

    final SegmentArrayWithData newSegments = tokensContainer.mySegments.copy();
    newSegments.shiftSegments(oldEndIndex, shift);
    newSegments.replace(startIndex, oldEndIndex, insertSegments);
    return new TokensContainer(tokensContainer.myHighlighter, tokensContainer.myLanguageID, newSegments);
  }

}
