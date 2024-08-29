// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.java.lexer;

import com.intellij.lexer.LexerBase;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.TokenType;
import com.intellij.psi.impl.source.AbstractBasicJavaDocElementTypeFactory;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.text.CharArrayUtil;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public class BasicJavaLexer extends LexerBase {

  private static final int STATE_DEFAULT = 0;
  private static final int STATE_TEXT_BLOCK_TEMPLATE = 1;

  private final _JavaLexer myFlexLexer;
  @SuppressWarnings("SSBasedInspection")
  private final IntArrayList myStateStack = new IntArrayList(1);
  private CharSequence myBuffer;
  private char @Nullable [] myBufferArray;
  private int myBufferIndex;
  private int myBufferEndOffset;
  private int myTokenEndOffset;  // positioned after the last symbol of the current token
  private IElementType myTokenType;

  /** The length of the last valid unicode escape (6 or greater), or 1 when no unicode escape was found. */
  private int mySymbolLength = 1;

  private final AbstractBasicJavaDocElementTypeFactory.JavaDocElementTypeContainer myJavaDocElementTypeContainer;
  public BasicJavaLexer(@NotNull LanguageLevel level, @NotNull AbstractBasicJavaDocElementTypeFactory javaDocElementTypeFactory) {
    myFlexLexer = new _JavaLexer(level);
    myJavaDocElementTypeContainer = javaDocElementTypeFactory.getContainer();
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
    myStateStack.push(initialState);
    myFlexLexer.reset(myBuffer, startOffset, endOffset, 0);
  }

  @Override
  public int getState() {
    return myStateStack.topInt();
  }

  @Override
  public IElementType getTokenType() {
    locateToken();
    return myTokenType;
  }

  @Override
  public int getTokenStart() {
    locateToken();
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
        int count1 = myStateStack.topInt() >> 16;
        if (count1 > 0) myStateStack.push((myStateStack.popInt() & STATE_TEXT_BLOCK_TEMPLATE) | ((count1 + 1) << 16));
        myTokenType = JavaTokenType.LBRACE;
        myTokenEndOffset = myBufferIndex + mySymbolLength;
        break;
      case '}':
        int count2 = myStateStack.topInt() >> 16;
        if (count2 > 0) {
          if (count2 != 1) {
            myStateStack.push((myStateStack.popInt() & STATE_TEXT_BLOCK_TEMPLATE) | ((count2 - 1) << 16));
          }
          else {
            int state = myStateStack.popInt();
            if (myStateStack.isEmpty()) myStateStack.push(STATE_DEFAULT);
            if ((state & STATE_TEXT_BLOCK_TEMPLATE) != 0) {
              boolean fragment = locateLiteralEnd(myBufferIndex + mySymbolLength, LiteralType.TEXT_BLOCK);
              myTokenType = fragment ? JavaTokenType.TEXT_BLOCK_TEMPLATE_MID : JavaTokenType.TEXT_BLOCK_TEMPLATE_END;
            }
            else {
              boolean fragment = locateLiteralEnd(myBufferIndex + mySymbolLength, LiteralType.STRING);
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
            int l2 = mySymbolLength;
            if (myBufferIndex + l1 + l2 < myBufferEndOffset && locateCharAt(myBufferIndex + l1 + l2) == '/') {
              int l3 = mySymbolLength;
              if(myBufferIndex + l1 + l2 + l3 < myBufferEndOffset && locateCharAt(myBufferIndex + l1 + l2 + l3) == '/') {
                // Long end of line comment
                myTokenType = JavaTokenType.END_OF_LINE_COMMENT;
                myTokenEndOffset = getLineTerminator(myBufferIndex + l1 + l2);
              } else {
                // Java 23 Markdown comments
                myTokenType = myJavaDocElementTypeContainer.DOC_COMMENT;
                myTokenEndOffset = getClosingMarkdownComment(myBufferIndex + l1 + l2);
              }
            } else {
              myTokenType = JavaTokenType.END_OF_LINE_COMMENT;
              myTokenEndOffset = getLineTerminator(myBufferIndex + l1 + l2);
            }
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
                myTokenType = myJavaDocElementTypeContainer.DOC_COMMENT;
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
        if (myBufferIndex == 0 && mySymbolLength == 1 && myBufferEndOffset > 1 && charAt(1) == '!') {
          myTokenType = JavaTokenType.END_OF_LINE_COMMENT;
          myTokenEndOffset = getLineTerminator(2);
        }
        else {
          flexLocateToken();
        }
        break;

      case '\'':
        myTokenType = JavaTokenType.CHARACTER_LITERAL;
        locateLiteralEnd(myBufferIndex + mySymbolLength, LiteralType.CHAR);
        break;

      case '"':
        int l1 = mySymbolLength;
        if (myBufferIndex + l1 < myBufferEndOffset && locateCharAt(myBufferIndex + l1) == '"') {
          int l2 = mySymbolLength;
          if (myBufferIndex + l1 + l2 < myBufferEndOffset && locateCharAt(myBufferIndex + l1 + l2) == '"') {
            boolean fragment = locateLiteralEnd(myBufferIndex + l1 + l2 + mySymbolLength, LiteralType.TEXT_BLOCK);
            myTokenType = fragment ? JavaTokenType.TEXT_BLOCK_TEMPLATE_BEGIN : JavaTokenType.TEXT_BLOCK_LITERAL;
          }
          else {
            myTokenType = JavaTokenType.STRING_LITERAL;
            myTokenEndOffset = myBufferIndex + l1 + l2;
          }
        }
        else {
          boolean fragment = locateLiteralEnd(myBufferIndex + l1, LiteralType.STRING);
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
    return getChars(offset, " \t\n\r\f");
  }

  private int getSimpleWhitespaces(int offset) {
    return getChars(offset, " \t");
  }

  /** @return The new position if none of the chars were detected */
  private int getChars(int offset, CharSequence charsToDetect) {
    int pos = offset;
    while (pos < myBufferEndOffset) {
      boolean detected = false;
      char c = locateCharAt(pos);
      for (int i= 0; i < charsToDetect.length(); i++) {
        if (charsToDetect.charAt(i) == c) {
          pos += mySymbolLength;
          detected = true;
          break;
        }
      }

      if(!detected) break;
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
   * @param literalType  the type of string literal.
   * @return {@code true} if this is a string template fragment, {@code false} otherwise.
   */
  private boolean locateLiteralEnd(int offset, LiteralType literalType) {
    int pos = offset;

    while (pos < myBufferEndOffset) {
      char c = locateCharAt(pos);

      if (c == '\\') {
        pos += mySymbolLength;
        // on (encoded) backslash we also need to skip the next symbol (e.g. \\u005c" is translated to \")
        if (pos < myBufferEndOffset) {
          if (locateCharAt(pos) == '{' && literalType != LiteralType.CHAR) {
            pos += mySymbolLength;
            myTokenEndOffset = pos;
            if (myStateStack.topInt() == 0) myStateStack.popInt();
            if (literalType == LiteralType.TEXT_BLOCK) {
              myStateStack.push(STATE_TEXT_BLOCK_TEMPLATE | (1 << 16));
            }
            else {
              myStateStack.push(STATE_DEFAULT | (1 << 16));
            }
            return true;
          }
        }
      }
      else if (c == literalType.c) {
        if (literalType == LiteralType.TEXT_BLOCK) {
          if ((pos += mySymbolLength) < myBufferEndOffset && locateCharAt(pos) == '"') {
            if ((pos += mySymbolLength) < myBufferEndOffset && locateCharAt(pos) == '"') {
              myTokenEndOffset = pos + mySymbolLength;
              return false;
            }
          }
          continue;
        }
        else {
          myTokenEndOffset = pos + mySymbolLength;
          return false;
        }
      }
      else if ((c == '\n' || c == '\r') && mySymbolLength == 1 && literalType != LiteralType.TEXT_BLOCK) {
        myTokenEndOffset = pos;
        return false;
      }
      pos += mySymbolLength;
    }
    myTokenEndOffset = pos;
    return false;
  }

  private int getClosingMarkdownComment(int offset) {
    int pos = offset;
    while(pos < myBufferEndOffset) {
      pos = getLineTerminator(pos);
      // Account for whitespaces beforehand
      int newPos = getSimpleWhitespaces(pos + mySymbolLength);

      newPos = getCharSeqAt(newPos, "///");
      if(newPos == -1) break;
      pos = newPos;
    }

    return pos;
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

  /** @return The position after of the last char, -1 otherwise */
  private int getCharSeqAt(int offset, CharSequence charSequence) {
    int pos = offset;
    for(int i = 0; i < charSequence.length(); i++) {
      if(!isLocatedCharAt(pos, charSequence.charAt(i))) return -1;
      pos += mySymbolLength;
    }
    return pos;
  }

  private boolean isLocatedCharAt(int offset, char charToDetect) {
    return (offset < myBufferEndOffset) && locateCharAt(offset) == charToDetect;
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

  enum LiteralType {
    STRING('"'), CHAR('\''), TEXT_BLOCK('"');

    final char c;
    LiteralType(char c) {
      this.c = c;
    }
  }
}
