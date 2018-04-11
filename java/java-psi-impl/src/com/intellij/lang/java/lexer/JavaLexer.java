// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.java.lexer;

import com.intellij.lexer.LexerBase;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.TokenType;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.text.CharSequenceHashingStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Set;

import static com.intellij.psi.PsiKeyword.*;

public class JavaLexer extends LexerBase {
  private static final Set<String> KEYWORDS = ContainerUtil.newTroveSet(
    ABSTRACT, BOOLEAN, BREAK, BYTE, CASE, CATCH, CHAR, CLASS, CONST, CONTINUE, DEFAULT, DO, DOUBLE, ELSE, EXTENDS, FINAL, FINALLY,
    FLOAT, FOR, GOTO, IF, IMPLEMENTS, IMPORT, INSTANCEOF, INT, INTERFACE, LONG, NATIVE, NEW, PACKAGE, PRIVATE, PROTECTED, PUBLIC,
    RETURN, SHORT, STATIC, STRICTFP, SUPER, SWITCH, SYNCHRONIZED, THIS, THROW, THROWS, TRANSIENT, TRY, VOID, VOLATILE, WHILE,
    TRUE, FALSE, NULL);

  private static final Set<CharSequence> JAVA9_KEYWORDS = ContainerUtil.newTroveSet(
    CharSequenceHashingStrategy.CASE_SENSITIVE,
    OPEN, MODULE, REQUIRES, EXPORTS, OPENS, USES, PROVIDES, TRANSITIVE, TO, WITH);

  public static boolean isKeyword(String id, @NotNull LanguageLevel level) {
    return KEYWORDS.contains(id) ||
           level.isAtLeast(LanguageLevel.JDK_1_4) && ASSERT.equals(id) ||
           level.isAtLeast(LanguageLevel.JDK_1_5) && ENUM.equals(id);
  }

  public static boolean isSoftKeyword(CharSequence id, @NotNull LanguageLevel level) {
    return id != null &&
           (level.isAtLeast(LanguageLevel.JDK_1_9) && JAVA9_KEYWORDS.contains(id) ||
            level.isAtLeast(LanguageLevel.JDK_10) && VAR.contentEquals(id));
  }

  private final _JavaLexer myFlexLexer;
  private CharSequence myBuffer;
  private @Nullable char[] myBufferArray;
  private int myBufferIndex;
  private int myBufferEndOffset;
  private int myTokenEndOffset;  // positioned after the last symbol of the current token
  private IElementType myTokenType;

  public JavaLexer(@NotNull LanguageLevel level) {
    myFlexLexer = new _JavaLexer(level);
  }

  @Override
  public final void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
    myBuffer = buffer;
    myBufferArray = CharArrayUtil.fromSequenceWithoutCopying(buffer);
    myBufferIndex = startOffset;
    myBufferEndOffset = endOffset;
    myTokenType = null;
    myTokenEndOffset = startOffset;
    myFlexLexer.reset(myBuffer, startOffset, endOffset, 0);
  }

  @Override
  public int getState() {
    return 0;
  }

  @Override
  public final IElementType getTokenType() {
    locateToken();
    return myTokenType;
  }

  @Override
  public final int getTokenStart() {
    return myBufferIndex;
  }

  @Override
  public final int getTokenEnd() {
    locateToken();
    return myTokenEndOffset;
  }

  @Override
  public final void advance() {
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
        myTokenEndOffset = getWhitespaces(myBufferIndex + 1);
        break;

      case '/':
        if (myBufferIndex + 1 >= myBufferEndOffset) {
          myTokenType = JavaTokenType.DIV;
          myTokenEndOffset = myBufferEndOffset;
        }
        else {
          char nextChar = charAt(myBufferIndex + 1);
          if (nextChar == '/') {
            myTokenType = JavaTokenType.END_OF_LINE_COMMENT;
            myTokenEndOffset = getLineTerminator(myBufferIndex + 2);
          }
          else if (nextChar == '*') {
            if (myBufferIndex + 2 >= myBufferEndOffset ||
                (charAt(myBufferIndex + 2)) != '*' ||
                (myBufferIndex + 3 < myBufferEndOffset &&
                 (charAt(myBufferIndex + 3)) == '/')) {
              myTokenType = JavaTokenType.C_STYLE_COMMENT;
              myTokenEndOffset = getClosingComment(myBufferIndex + 2);
            }
            else {
              myTokenType = JavaDocElementType.DOC_COMMENT;
              myTokenEndOffset = getClosingComment(myBufferIndex + 3);
            }
          }
          else {
            flexLocateToken();
          }
        }
        break;

      case '"':
      case '\'':
        myTokenType = c == '"' ? JavaTokenType.STRING_LITERAL : JavaTokenType.CHARACTER_LITERAL;
        myTokenEndOffset = getClosingQuote(myBufferIndex + 1, c);
        break;

      case '`':
        myTokenType = JavaTokenType.RAW_STRING_LITERAL;
        myTokenEndOffset = getRawLiteralEnd(myBufferIndex);
        break;

      default:
        flexLocateToken();
    }

    if (myTokenEndOffset > myBufferEndOffset) {
      myTokenEndOffset = myBufferEndOffset;
    }
  }

  private int getWhitespaces(int offset) {
    if (offset >= myBufferEndOffset) {
      return myBufferEndOffset;
    }

    int pos = offset;
    char c = charAt(pos);

    while (c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\f') {
      pos++;
      if (pos == myBufferEndOffset) return pos;
      c = charAt(pos);
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
    if (offset >= myBufferEndOffset) {
      return myBufferEndOffset;
    }

    int pos = offset;
    char c = charAt(pos);

    while (true) {
      while (c != quoteChar && c != '\n' && c != '\r' && c != '\\') {
        pos++;
        if (pos >= myBufferEndOffset) return myBufferEndOffset;
        c = charAt(pos);
      }

      if (c == '\\') {
        pos++;
        if (pos >= myBufferEndOffset) return myBufferEndOffset;
        c = charAt(pos);
        if (c == '\n' || c == '\r') continue;
        pos++;
        if (pos >= myBufferEndOffset) return myBufferEndOffset;
        c = charAt(pos);
      }
      else if (c == quoteChar) {
        break;
      }
      else {
        pos--;
        break;
      }
    }

    return pos + 1;
  }

  private int getClosingComment(int offset) {
    int pos = offset;

    while (pos < myBufferEndOffset - 1) {
      char c = charAt(pos);
      if (c == '*' && (charAt(pos + 1)) == '/') {
        break;
      }
      pos++;
    }

    return pos + 2;
  }

  private int getLineTerminator(int offset) {
    int pos = offset;

    while (pos < myBufferEndOffset) {
      char c = charAt(pos);
      if (c == '\r' || c == '\n') break;
      pos++;
    }

    return pos;
  }

  private int getRawLiteralEnd(int offset) {
    int pos = offset;

    while (pos < myBufferEndOffset && charAt(pos) == '`') pos++;
    int quoteLen = pos - offset;

    int start;
    do {
      while (pos < myBufferEndOffset && charAt(pos) != '`') pos++;
      start = pos;
      while (pos < myBufferEndOffset && charAt(pos) == '`') pos++;
    }
    while (pos - start != quoteLen && pos < myBufferEndOffset);

    return pos;
  }

  private char charAt(int position) {
    return myBufferArray != null ? myBufferArray[position] : myBuffer.charAt(position);
  }

  @NotNull
  @Override
  public CharSequence getBufferSequence() {
    return myBuffer;
  }

  @Override
  public final int getBufferEnd() {
    return myBufferEndOffset;
  }
}