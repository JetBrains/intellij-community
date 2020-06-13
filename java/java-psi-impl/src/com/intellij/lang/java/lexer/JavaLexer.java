// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;

import static com.intellij.psi.PsiKeyword.*;

public class JavaLexer extends LexerBase {
  private static final Set<String> KEYWORDS = ContainerUtil.newTroveSet(
    ABSTRACT, BOOLEAN, BREAK, BYTE, CASE, CATCH, CHAR, CLASS, CONST, CONTINUE, DEFAULT, DO, DOUBLE, ELSE, EXTENDS, FINAL, FINALLY,
    FLOAT, FOR, GOTO, IF, IMPLEMENTS, IMPORT, INSTANCEOF, INT, INTERFACE, LONG, NATIVE, NEW, PACKAGE, PRIVATE, PROTECTED, PUBLIC,
    RETURN, SHORT, STATIC, STRICTFP, SUPER, SWITCH, SYNCHRONIZED, THIS, THROW, THROWS, TRANSIENT, TRY, VOID, VOLATILE, WHILE,
    TRUE, FALSE, NULL, NON_SEALED);

  private static final Set<CharSequence> JAVA9_KEYWORDS =
    new THashSet<>(Arrays.asList(OPEN, MODULE, REQUIRES, EXPORTS, OPENS, USES, PROVIDES, TRANSITIVE, TO, WITH),
                   CharSequenceHashingStrategy.CASE_SENSITIVE);

  // Minus is a single token that may separate identifiers that can form "non-sealed" keyword
  // For incremental lexing it is important that at "-" lexer is not in the initial state so we will lex one more token
  private static final int INITIAL_STATE = 0;
  private static final int MAYBE_NON_SEALED_STATE = -1;

  public static boolean isKeyword(String id, @NotNull LanguageLevel level) {
    return KEYWORDS.contains(id) ||
           level.isAtLeast(LanguageLevel.JDK_1_4) && ASSERT.equals(id) ||
           level.isAtLeast(LanguageLevel.JDK_1_5) && ENUM.equals(id);
  }

  public static boolean isSoftKeyword(CharSequence id, @NotNull LanguageLevel level) {
    return id != null &&
           (level.isAtLeast(LanguageLevel.JDK_1_9) && JAVA9_KEYWORDS.contains(id) ||
            level.isAtLeast(LanguageLevel.JDK_10) && VAR.contentEquals(id) ||
            level.isAtLeast(LanguageLevel.JDK_14_PREVIEW) && RECORD.contentEquals(id) ||
            level.isAtLeast(LanguageLevel.JDK_14) && YIELD.contentEquals(id) ||
            (level.isAtLeast(LanguageLevel.JDK_15_PREVIEW) && (SEALED.contentEquals(id) || PERMITS.contentEquals(id)))
           );
  }

  private final _JavaLexer myFlexLexer;
  private final LanguageLevel myLevel;
  private CharSequence myBuffer;
  private char @Nullable [] myBufferArray;
  private int myBufferIndex;
  private int myBufferEndOffset;
  private int myTokenEndOffset;  // positioned after the last symbol of the current token
  private IElementType myTokenType;
  private int myState = INITIAL_STATE;

  public JavaLexer(@NotNull LanguageLevel level) {
    myLevel = level;
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
    return myState;
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

    boolean wasMinus = false;
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

      case '\'':
        myTokenType = JavaTokenType.CHARACTER_LITERAL;
        myTokenEndOffset = getClosingQuote(myBufferIndex + 1, c);
        break;

      case '"':
        if (myBufferIndex + 2 < myBufferEndOffset && charAt(myBufferIndex + 2) == '"' && charAt(myBufferIndex + 1) == '"') {
          myTokenType = JavaTokenType.TEXT_BLOCK_LITERAL;
          myTokenEndOffset = getTextBlockEnd(myBufferIndex + 2);
        }
        else {
          myTokenType = JavaTokenType.STRING_LITERAL;
          myTokenEndOffset = getClosingQuote(myBufferIndex + 1, c);
        }
        break;
      case 'n':
        if (myLevel.isAtLeast(LanguageLevel.JDK_15_PREVIEW) &&
            myBufferIndex + 9 < myBufferEndOffset &&
            charAt(myBufferIndex + 1) == 'o' &&
            charAt(myBufferIndex + 2) == 'n' &&
            charAt(myBufferIndex + 3) == '-' &&
            charAt(myBufferIndex + 4) == 's' &&
            charAt(myBufferIndex + 5) == 'e' &&
            charAt(myBufferIndex + 6) == 'a' &&
            charAt(myBufferIndex + 7) == 'l' &&
            charAt(myBufferIndex + 8) == 'e' &&
            charAt(myBufferIndex + 9) == 'd') {
          myTokenType = JavaTokenType.NON_SEALED_KEYWORD;
          myTokenEndOffset = myBufferIndex + 10;
        }
        else {
          flexLocateToken();
        }
        break;
      case '-':
        wasMinus = true;
        // fallthrough
      default:
        flexLocateToken();
    }
    if (wasMinus) {
      myState = MAYBE_NON_SEALED_STATE;
    } else {
      myState = INITIAL_STATE;
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

  private int getTextBlockEnd(int offset) {
    int pos = offset;

    while ((pos = getClosingQuote(pos + 1, '"')) < myBufferEndOffset) {
      char current = charAt(pos);
      if (current == '\\') {
        pos++;
      }
      else if (current == '"' && pos + 1 < myBufferEndOffset && charAt(pos + 1) == '"') {
        pos += 2;
        break;
      }
    }

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