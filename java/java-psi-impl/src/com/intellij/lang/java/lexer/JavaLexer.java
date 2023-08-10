// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
  private static final int STATE_DEFAULT = 0;
  private static final int STATE_TEXT_BLOCK_TEMPLATE = 1;

  private static final Set<String> KEYWORDS = ContainerUtil.immutableSet(
    ABSTRACT, BOOLEAN, BREAK, BYTE, CASE, CATCH, CHAR, CLASS, CONST, CONTINUE, DEFAULT, DO, DOUBLE, ELSE, EXTENDS, FINAL, FINALLY,
    FLOAT, FOR, GOTO, IF, IMPLEMENTS, IMPORT, INSTANCEOF, INT, INTERFACE, LONG, NATIVE, NEW, PACKAGE, PRIVATE, PROTECTED, PUBLIC,
    RETURN, SHORT, STATIC, STRICTFP, SUPER, SWITCH, SYNCHRONIZED, THIS, THROW, THROWS, TRANSIENT, TRY, VOID, VOLATILE, WHILE,
    TRUE, FALSE, NULL, NON_SEALED);

  private static final Set<CharSequence> JAVA9_KEYWORDS = CollectionFactory.createCharSequenceSet(Arrays.asList(OPEN, MODULE, REQUIRES, EXPORTS, OPENS, USES, PROVIDES, TRANSITIVE, TO, WITH));

  public static boolean isKeyword(@NotNull String id, @NotNull LanguageLevel level) {
    return KEYWORDS.contains(id) ||
           level.isAtLeast(LanguageLevel.JDK_1_4) && ASSERT.equals(id) ||
           level.isAtLeast(LanguageLevel.JDK_1_5) && ENUM.equals(id);
  }

  public static boolean isSoftKeyword(@NotNull CharSequence id, @NotNull LanguageLevel level) {
    return level.isAtLeast(LanguageLevel.JDK_1_9) && JAVA9_KEYWORDS.contains(id) ||
           level.isAtLeast(LanguageLevel.JDK_10) && VAR.contentEquals(id) ||
           level.isAtLeast(LanguageLevel.JDK_16) && RECORD.contentEquals(id) ||
           level.isAtLeast(LanguageLevel.JDK_14) && YIELD.contentEquals(id) ||
           level.isAtLeast(LanguageLevel.JDK_17) && (SEALED.contentEquals(id) || PERMITS.contentEquals(id)) ||
           level.isAtLeast(LanguageLevel.JDK_20_PREVIEW) && WHEN.contentEquals(id);
  }

  private final _JavaLexer myFlexLexer;
  private final boolean myStringTemplates;
  private CharSequence myBuffer;
  private char @Nullable [] myBufferArray;
  private int myBufferIndex;
  private int myBufferEndOffset;
  private int myTokenEndOffset;  // positioned after the last symbol of the current token
  private IElementType myTokenType;
  private short myState = 0;
  private short[] myBraceCounts = new short[1];
  private int myTemplateDepth = -1;

  /** The length of the last valid unicode escape (6 or greater), or 1 when no unicode escape was found. */
  private int mySymbolLength = 1;

  public JavaLexer(@NotNull LanguageLevel level) {
    myFlexLexer = new _JavaLexer(level);
    myStringTemplates = level.isAtLeast(LanguageLevel.JDK_21_PREVIEW);
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
    if (initialState != 0) {
      myState = (short)initialState;
      short braceCount = (short)(initialState >> 16);
      if (braceCount > 0) {
        myBraceCounts[0] = braceCount;
        myTemplateDepth = 0;
      }
    }
    myFlexLexer.reset(myBuffer, startOffset, endOffset, 0);
  }

  @Override
  public int getState() {
    return myTemplateDepth < 0 ? 0 : myBraceCounts[myTemplateDepth] << 16 | myState;
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

  /**
   * Handles whitespace, comment, string literal, text block and string template tokens. Other tokens are handled by calling
   * the flex lexer.
   */
  private void locateToken() {
    if (myTokenType != null) return;

    if (myTokenEndOffset == myBufferEndOffset) {
      myBufferIndex = myBufferEndOffset;
      return;
    }

    myBufferIndex = myTokenEndOffset;

    char c = locateCharAt(myBufferIndex);
    switch (c) {
      case ' ':
      case '\t':
      case '\n':
      case '\r':
      case '\f':
        myTokenType = TokenType.WHITE_SPACE;
        myTokenEndOffset = getWhitespaces(myBufferIndex + mySymbolLength);
        break;

      case '{':
        if (myTemplateDepth >= 0) {
          myBraceCounts[myTemplateDepth]++;
        }
        myTokenType = JavaTokenType.LBRACE;
        myTokenEndOffset = myBufferIndex + mySymbolLength;
        break;
      case '}':
        if (myTemplateDepth >= 0) {
          if (--myBraceCounts[myTemplateDepth] == 0) {
            myTemplateDepth--;
            if (myState == STATE_TEXT_BLOCK_TEMPLATE) {
              if (locateTextBlockEnd(myBufferIndex + mySymbolLength)) {
                myTokenType = JavaTokenType.TEXT_BLOCK_TEMPLATE_MID;
              }
              else {
                myState = STATE_DEFAULT;
                myTokenType = JavaTokenType.TEXT_BLOCK_TEMPLATE_END;
              }
            }
            else {
              boolean fragment = locateLiteralEnd(myBufferIndex + mySymbolLength, '"');
              myTokenType = fragment ? JavaTokenType.STRING_TEMPLATE_MID : JavaTokenType.STRING_TEMPLATE_END;
            }
            break;
          }
        }
        myTokenType = JavaTokenType.RBRACE;
        myTokenEndOffset = myBufferIndex + mySymbolLength;
        break;
      case '/':
        if (myBufferIndex + mySymbolLength >= myBufferEndOffset) {
          myTokenType = JavaTokenType.DIV;
          myTokenEndOffset = myBufferEndOffset;
        }
        else {
          int l1 = mySymbolLength;
          char nextChar = locateCharAt(myBufferIndex + l1);
          if (nextChar == '/') {
            myTokenType = JavaTokenType.END_OF_LINE_COMMENT;
            myTokenEndOffset = getLineTerminator(myBufferIndex + l1 + mySymbolLength);
          }
          else if (nextChar == '*') {
            int l2 = mySymbolLength;
            if (myBufferIndex + l1 + l2 < myBufferEndOffset && locateCharAt(myBufferIndex + l1 + l2) == '*') {
              int l3 = mySymbolLength;
              if (myBufferIndex + l1 + l2 + l3 < myBufferEndOffset && locateCharAt(myBufferIndex + l1 + l2 + l3) == '/') {
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

      case '#': // this assumes the Unix shell used does not understand Unicode escapes sequences
        if (myBufferIndex == 0 && mySymbolLength == 1 && myBufferEndOffset > 1 && charAt(1) == '!' && mySymbolLength == 1) {
          myTokenType = JavaTokenType.END_OF_LINE_COMMENT;
          myTokenEndOffset = getLineTerminator(2);
        }
        else {
          flexLocateToken();
        }
        break;

      case '\'':
        myTokenType = JavaTokenType.CHARACTER_LITERAL;
        locateLiteralEnd(myBufferIndex + mySymbolLength, '\'');
        break;

      case '"':
        int l1 = mySymbolLength;
        if (myBufferIndex + l1 < myBufferEndOffset && locateCharAt(myBufferIndex + l1) == '"') {
          int l2 = mySymbolLength;
          if (myBufferIndex + l1 + l2 < myBufferEndOffset && locateCharAt(myBufferIndex + l1 + l2) == '"') {
            boolean fragment = locateTextBlockEnd(myBufferIndex + l1 + l2 + mySymbolLength);
            if (fragment) {
              myState = STATE_TEXT_BLOCK_TEMPLATE;
              myTokenType = JavaTokenType.TEXT_BLOCK_TEMPLATE_BEGIN;
            }
            else {
              myTokenType = JavaTokenType.TEXT_BLOCK_LITERAL;
            }
          }
          else {
            myTokenType = JavaTokenType.STRING_LITERAL;
            myTokenEndOffset = myBufferIndex + l1 + l2;
          }
        }
        else {
          boolean fragment = locateLiteralEnd(myBufferIndex + l1, '"');
          myTokenType = fragment ? JavaTokenType.STRING_TEMPLATE_BEGIN : JavaTokenType.STRING_LITERAL;
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
      char c = locateCharAt(pos);
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

  /**
   * @param offset  the offset to start.
   * @param quoteChar  the type of quote the literal should end with.
   * @return {@code true} if this is a string template fragment, {@code false} otherwise.
   */
  private boolean locateLiteralEnd(int offset, char quoteChar) {
    int pos = offset;

    while (pos < myBufferEndOffset) {
      char c = locateCharAt(pos);

      if (c == '\\') {
        pos += mySymbolLength;
        // on (encoded) backslash we also need to skip the next symbol (e.g. \\u005c" is translated to \")
        if (pos < myBufferEndOffset) {
          if (locateCharAt(pos) == '{' && myStringTemplates && quoteChar == '"') {
            pos += mySymbolLength;
            myTemplateDepth++;
            if (myTemplateDepth == myBraceCounts.length) {
              myBraceCounts = Arrays.copyOf(myBraceCounts, myTemplateDepth * 2);
            }
            myBraceCounts[myTemplateDepth] = 1;
            myTokenEndOffset = pos;
            return true;
          }
        }
      }
      else if (c == quoteChar) {
        myTokenEndOffset = pos + mySymbolLength;
        return false;
      }
      else if ((c == '\n' || c == '\r') && mySymbolLength == 1) {
        myTokenEndOffset = pos;
        return false;
      }
      pos += mySymbolLength;
    }
    myTokenEndOffset = pos;
    return false;
  }

  private int getClosingComment(int offset) {
    int pos = offset;

    while (pos < myBufferEndOffset) {
      char c = locateCharAt(pos);
      pos += mySymbolLength;
      if (c == '*' && pos < myBufferEndOffset && locateCharAt(pos) == '/') break;
    }

    return pos + mySymbolLength;
  }

  private int getLineTerminator(int offset) {
    int pos = offset;

    while (pos < myBufferEndOffset) {
      char c = locateCharAt(pos);
      if (c == '\r' || c == '\n') break;
      pos += mySymbolLength;
    }

    return pos;
  }

  /**
   * @param offset  the offset to start.
   * @return {@code true} if this is a string template fragment, {@code false} otherwise.
   */
  private boolean locateTextBlockEnd(int offset) {
    int pos = offset;
    boolean fragment;

    while (!(fragment = locateLiteralEnd(pos, '"')) && (pos = myTokenEndOffset) < myBufferEndOffset) {
      char c = locateCharAt(pos);
      pos += mySymbolLength;
      if (c == '\\') {
        locateCharAt(pos); // skip escaped char
        pos += mySymbolLength;
      }
      else if (c == '"' && pos < myBufferEndOffset && locateCharAt(pos) == '"') {
        myTokenEndOffset = pos + mySymbolLength;
        return false;
      }
    }

    return fragment;
  }

  private char charAt(int offset) {
    return myBufferArray != null ? myBufferArray[offset] : myBuffer.charAt(offset);
  }

  private char locateCharAt(int offset) {
    mySymbolLength = 1;
    char first = charAt(offset);
    if (first != '\\') return first;
    int pos = offset + 1;
    if (pos < myBufferEndOffset && charAt(pos) == '\\') return first;
    boolean escaped = true;
    int i = offset;
    while (--i >= 0 && charAt(i) == '\\') escaped = !escaped;
    if (!escaped) return first;
    if (pos < myBufferEndOffset && charAt(pos) != 'u') return first;
    //noinspection StatementWithEmptyBody
    while (++pos < myBufferEndOffset && charAt(pos) == 'u');
    if (pos + 3 >= myBufferEndOffset) return first;
    int result = 0;
    for (int max = pos + 4; pos < max; pos++) {
      result <<= 4;
      char c = charAt(pos);
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
