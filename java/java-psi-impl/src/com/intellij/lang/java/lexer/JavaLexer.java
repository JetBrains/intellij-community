// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.java.lexer;

import com.intellij.lexer.LexerBase;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.TokenType;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;

import static com.intellij.psi.PsiKeyword.*;

public final class JavaLexer extends LexerBase {
  private static final Set<String> KEYWORDS = ContainerUtil.set(
    ABSTRACT, BOOLEAN, BREAK, BYTE, CASE, CATCH, CHAR, CLASS, CONST, CONTINUE, DEFAULT, DO, DOUBLE, ELSE, EXTENDS, FINAL, FINALLY,
    FLOAT, FOR, GOTO, IF, IMPLEMENTS, IMPORT, INSTANCEOF, INT, INTERFACE, LONG, NATIVE, NEW, PACKAGE, PRIVATE, PROTECTED, PUBLIC,
    RETURN, SHORT, STATIC, STRICTFP, SUPER, SWITCH, SYNCHRONIZED, THIS, THROW, THROWS, TRANSIENT, TRY, VOID, VOLATILE, WHILE,
    TRUE, FALSE, NULL, NON_SEALED);

  private static final Set<CharSequence> JAVA9_KEYWORDS = CollectionFactory.createCharSequenceSet(Arrays.asList(OPEN, MODULE, REQUIRES, EXPORTS, OPENS, USES, PROVIDES, TRANSITIVE, TO, WITH));

  public static boolean isKeyword(String id, @NotNull LanguageLevel level) {
    return KEYWORDS.contains(id) ||
           level.isAtLeast(LanguageLevel.JDK_1_4) && ASSERT.equals(id) ||
           level.isAtLeast(LanguageLevel.JDK_1_5) && ENUM.equals(id);
  }

  public static boolean isSoftKeyword(CharSequence id, @NotNull LanguageLevel level) {
    return id != null &&
           (level.isAtLeast(LanguageLevel.JDK_1_9) && JAVA9_KEYWORDS.contains(id) ||
            level.isAtLeast(LanguageLevel.JDK_10) && VAR.contentEquals(id) ||
            level.isAtLeast(LanguageLevel.JDK_16) && RECORD.contentEquals(id) ||
            level.isAtLeast(LanguageLevel.JDK_14) && YIELD.contentEquals(id) ||
            (level.isAtLeast(LanguageLevel.JDK_17) && (SEALED.contentEquals(id) || PERMITS.contentEquals(id))));
  }

  private final _JavaLexer myFlexLexer;
  private CharSequence myBuffer;
  private char @Nullable [] myBufferArray;
  private int myBufferIndex;
  private int myBufferEndOffset;
  private int myTokenEndOffset;  // positioned after the last symbol of the current token
  private IElementType myTokenType;

  /** The length of the last valid unicode escape (6 or greater), or 1 when no unicode escape was found. */
  private int mySymbolLength = 1;

  public JavaLexer(@NotNull LanguageLevel level) {
    myFlexLexer = new _JavaLexer(level);
  }

  @Override
  public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
    myBuffer = buffer;
    myBufferArray = CharArrayUtil.fromSequenceWithoutCopying(buffer);
    myBufferIndex = startOffset;
    myBufferEndOffset = endOffset;
    myTokenType = null;
    myTokenEndOffset = startOffset;
    mySymbolLength = 1;
    myFlexLexer.reset(myBuffer, startOffset, endOffset, 0);
  }

  @Override
  public int getState() {
    return 0;
  }

  @Override
  public IElementType getTokenType() {
    locateToken();
    return myTokenType;
  }

  @Override
  public int getTokenStart() {
    return myBufferIndex;
  }

  @Override
  public int getTokenEnd() {
    locateToken();
    return myTokenEndOffset;
  }

  @Override
  public void advance() {
    locateToken();
    myTokenType = null;
  }

  private void locateToken() {
    if (myTokenType != null) return;

    if (myTokenEndOffset == myBufferEndOffset) {
      myBufferIndex = myBufferEndOffset;
      return;
    }

    myBufferIndex = myTokenEndOffset;

    char c = charAt(myBufferIndex);
    switch (c) {
      case ' ':
      case '\t':
      case '\n':
      case '\r':
      case '\f':
        myTokenType = TokenType.WHITE_SPACE;
        myTokenEndOffset = getWhitespaces(myBufferIndex + mySymbolLength);
        break;

      case '/':
        if (myBufferIndex + mySymbolLength >= myBufferEndOffset) {
          myTokenType = JavaTokenType.DIV;
          myTokenEndOffset = myBufferEndOffset;
        }
        else {
          int l1 = mySymbolLength;
          char nextChar = charAt(myBufferIndex + l1);
          if (nextChar == '/') {
            myTokenType = JavaTokenType.END_OF_LINE_COMMENT;
            myTokenEndOffset = getLineTerminator(myBufferIndex + l1 + mySymbolLength);
          }
          else if (nextChar == '*') {
            int l2 = mySymbolLength;
            if (myBufferIndex + l1 + l2 < myBufferEndOffset && charAt(myBufferIndex + l1 + l2) == '*') {
              int l3 = mySymbolLength;
              if (myBufferIndex + l1 + l2 + l3 < myBufferEndOffset && charAt(myBufferIndex + l1 + l2 + l3) == '/') {
                myTokenType = JavaTokenType.C_STYLE_COMMENT;
                myTokenEndOffset = myBufferIndex + l1 + l2 + l3 + mySymbolLength;
              }
              else {
                myTokenType = JavaDocElementType.DOC_COMMENT;
                myTokenEndOffset = getClosingComment(myBufferIndex + l1 + l2 + l3);
              }
            }
            else {
              myTokenType = JavaTokenType.C_STYLE_COMMENT;
              myTokenEndOffset = getClosingComment(myBufferIndex + l1 + l2 + mySymbolLength);
            }
          }
          else {
            flexLocateToken();
          }
        }
        break;

      case '#' :
        if (mySymbolLength == 1 && myBufferIndex == 0 && myBufferIndex + 1 < myBufferEndOffset && charAt(myBufferIndex + 1) == '!' && mySymbolLength == 1) {
          myTokenType = JavaTokenType.END_OF_LINE_COMMENT;
          myTokenEndOffset = getLineTerminator(myBufferIndex + 2);
        }
        else {
          flexLocateToken();
        }
        break;

      case '\'':
        myTokenType = JavaTokenType.CHARACTER_LITERAL;
        myTokenEndOffset = getClosingQuote(myBufferIndex + mySymbolLength, '\'');
        break;

      case '"':
        int l1 = mySymbolLength;
        if (myBufferIndex + l1 < myBufferEndOffset && charAt(myBufferIndex + l1) == '"') {
          int l2 = mySymbolLength;
          if (myBufferIndex + l1 + l2 < myBufferEndOffset &&  charAt(myBufferIndex + l1 + l2) == '"') {
            myTokenType = JavaTokenType.TEXT_BLOCK_LITERAL;
            myTokenEndOffset = getTextBlockEnd(myBufferIndex + l1 + l2);
          }
          else {
            myTokenType = JavaTokenType.STRING_LITERAL;
            myTokenEndOffset = myBufferIndex + l1 + l2;
          }
        }
        else {
          myTokenType = JavaTokenType.STRING_LITERAL;
          myTokenEndOffset = getClosingQuote(myBufferIndex + l1, '"');
        }
        break;

      default:
        flexLocateToken();
    }

    if (myTokenEndOffset > myBufferEndOffset) {
      myTokenEndOffset = myBufferEndOffset;
    }
  }

  private int getWhitespaces(int offset) {
    int pos = offset;

    while (pos < myBufferEndOffset) {
      char c = charAt(pos);
      if (c != ' ' && c != '\t' && c != '\n' && c != '\r' && c != '\f') break;
      pos += mySymbolLength;
    }

    return pos;
  }

  private void flexLocateToken() {
    try {
      myFlexLexer.goTo(myBufferIndex);
      myTokenType = myFlexLexer.advance();
      myTokenEndOffset = myFlexLexer.getTokenEnd();
    }
    catch (IOException e) { /* impossible */ }
  }

  private int getClosingQuote(int offset, char quoteChar) {
    int pos = offset;

    while (pos < myBufferEndOffset) {
      char c = charAt(pos);

      if (c == '\\') {
        pos += mySymbolLength;
        // on (encoded) backslash we also need to skip the next symbol (e.g. \\u005c" is translated to \")
        if (pos < myBufferEndOffset) charAt(pos);
      }
      else if (c == quoteChar) {
        return pos + mySymbolLength;
      }
      else if ((c == '\n' || c == '\r') && mySymbolLength == 1) {
        return pos;
      }
      pos += mySymbolLength;
    }
    return pos;
  }

  private int getClosingComment(int offset) {
    int pos = offset;

    while (pos < myBufferEndOffset) {
      char c = charAt(pos);
      pos += mySymbolLength;
      if (c == '*' && pos < myBufferEndOffset && charAt(pos) == '/') break;
    }

    return pos + mySymbolLength;
  }

  private int getLineTerminator(int offset) {
    int pos = offset;

    while (pos < myBufferEndOffset) {
      char c = charAt(pos);
      if (c == '\r' || c == '\n') break;
      pos += mySymbolLength;
    }

    return pos;
  }

  private int getTextBlockEnd(int offset) {
    int pos = offset;

    while ((pos = getClosingQuote(pos + mySymbolLength, '"')) < myBufferEndOffset) {
      char c = charAt(pos);
      if (c == '\\') {
        pos += mySymbolLength;
        charAt(pos); // skip escaped char
      }
      else if (c == '"') {
        int l1 = mySymbolLength;
        if (pos + l1 < myBufferEndOffset && charAt(pos + l1) == '"') {
          return pos + l1 + mySymbolLength;
        }
      }
    }

    return pos;
  }

  private char _charAt(int offset) {
    return myBufferArray != null ? myBufferArray[offset] : myBuffer.charAt(offset);
  }

  private char charAt(int offset) {
    mySymbolLength = 1;
    int pos = offset;
    char first = _charAt(pos);
    if (first != '\\') return first;
    if (++pos >= myBufferEndOffset || _charAt(pos) != 'u') return first;
    //noinspection StatementWithEmptyBody
    while (++pos < myBufferEndOffset && _charAt(pos) == 'u');
    if (pos + 3 >= myBufferEndOffset) return first;
    int result = 0;
    for (int max = pos + 4; pos < max; pos++) {
      result <<= 4;
      char c = _charAt(pos);
      if ('0' <= c && c <= '9') result += c - '0';
      else if ('a' <= c && c <= 'f') result += (c - 'a') + 10;
      else if ('A' <= c && c <= 'F') result += (c - 'A') + 10;
      else return first;
    }
    mySymbolLength = pos - offset;
    return (char)result;
  }

  @NotNull
  @Override
  public CharSequence getBufferSequence() {
    return myBuffer;
  }

  @Override
  public int getBufferEnd() {
    return myBufferEndOffset;
  }
}
