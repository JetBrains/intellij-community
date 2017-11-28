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
            level.isAtLeast(LanguageLevel.JDK_X) && VAR.contentEquals(id));
  }

  private final _JavaLexer myFlexLexer;
  private CharSequence myBuffer;
  private char[] myBufferArray;
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
    if (myTokenType == null) _locateToken();
    return myTokenType;
  }

  @Override
  public final int getTokenStart() {
    return myBufferIndex;
  }

  @Override
  public final int getTokenEnd() {
    if (myTokenType == null) _locateToken();
    return myTokenEndOffset;
  }

  @Override
  public final void advance() {
    if (myTokenType == null) _locateToken();
    myTokenType = null;
  }

  private void _locateToken() {
    if (myTokenEndOffset == myBufferEndOffset) {
      myTokenType = null;
      myBufferIndex = myBufferEndOffset;
      return;
    }

    myBufferIndex = myTokenEndOffset;

    char c = myBufferArray != null ? myBufferArray[myBufferIndex] : myBuffer.charAt(myBufferIndex);
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
          char nextChar = myBufferArray != null ? myBufferArray[myBufferIndex + 1] : myBuffer.charAt(myBufferIndex + 1);
          if (nextChar == '/') {
            myTokenType = JavaTokenType.END_OF_LINE_COMMENT;
            myTokenEndOffset = getLineTerminator(myBufferIndex + 2);
          }
          else if (nextChar == '*') {
            if (myBufferIndex + 2 >= myBufferEndOffset ||
                (myBufferArray != null ? myBufferArray[myBufferIndex + 2] : myBuffer.charAt(myBufferIndex + 2)) != '*' ||
                (myBufferIndex + 3 < myBufferEndOffset &&
                 (myBufferArray != null ? myBufferArray[myBufferIndex + 3] : myBuffer.charAt(myBufferIndex + 3)) == '/')) {
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
        myTokenEndOffset = getClosingParenthesis(myBufferIndex + 1, c);
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
    char c = myBufferArray != null ? myBufferArray[pos] : myBuffer.charAt(pos);

    while (c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\f') {
      pos++;
      if (pos == myBufferEndOffset) return pos;
      c = myBufferArray != null ? myBufferArray[pos] : myBuffer.charAt(pos);
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

  private int getClosingParenthesis(int offset, char c) {
    if (offset >= myBufferEndOffset) {
      return myBufferEndOffset;
    }

    int pos = offset;
    char cur = myBufferArray != null ? myBufferArray[pos] : myBuffer.charAt(pos);

    while (true) {
      while (cur != c && cur != '\n' && cur != '\r' && cur != '\\') {
        pos++;
        if (pos >= myBufferEndOffset) return myBufferEndOffset;
        cur = myBufferArray != null ? myBufferArray[pos] : myBuffer.charAt(pos);
      }

      if (cur == '\\') {
        pos++;
        if (pos >= myBufferEndOffset) return myBufferEndOffset;
        cur = myBufferArray != null ? myBufferArray[pos] : myBuffer.charAt(pos);
        if (cur == '\n' || cur == '\r') continue;
        pos++;
        if (pos >= myBufferEndOffset) return myBufferEndOffset;
        cur = myBufferArray != null ? myBufferArray[pos] : myBuffer.charAt(pos);
      }
      else if (cur == c) {
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
      char c = myBufferArray != null ? myBufferArray[pos] : myBuffer.charAt(pos);
      if (c == '*' && (myBufferArray != null ? myBufferArray[pos + 1] : myBuffer.charAt(pos + 1)) == '/') {
        break;
      }
      pos++;
    }

    return pos + 2;
  }

  private int getLineTerminator(int offset) {
    int pos = offset;

    while (pos < myBufferEndOffset) {
      char c = myBufferArray != null ? myBufferArray[pos] : myBuffer.charAt(pos);
      if (c == '\r' || c == '\n') break;
      pos++;
    }

    return pos;
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