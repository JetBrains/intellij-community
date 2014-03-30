/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.TokenType;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.text.CharArrayUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Set;

public class JavaLexer extends LexerBase {
  private static final HashTable[] TABLES = new HashTable[]{
    new HashTable(LanguageLevel.JDK_1_5),
    new HashTable(LanguageLevel.JDK_1_4),
    new HashTable(LanguageLevel.JDK_1_3)
  };

  private static HashTable getTable(final LanguageLevel level) {
    for (HashTable table : TABLES) {
      if (level.isAtLeast(table.myLevel)) {
        return table;
      }
    }
    throw new IllegalArgumentException("Unsupported level: " + level);
  }

  public static boolean isKeyword(String id, LanguageLevel level) {
    return getTable(level).contains(id);
  }

  private final _JavaLexer myFlexLexer;
  private final HashTable myTable;
  private CharSequence myBuffer;
  private char[] myBufferArray;
  private int myBufferIndex;
  private int myBufferEndOffset;
  private int myTokenEndOffset;  // positioned after the last symbol of the current token
  private IElementType myTokenType;

  public JavaLexer(@NotNull final LanguageLevel level) {
    myFlexLexer = new _JavaLexer(level);
    myTable = getTable(level);
  }

  private static final class HashTable {
    private static final int NUM_ENTRIES = 999;
    private static final Logger LOG = Logger.getInstance("com.intellij.Lexer.JavaLexer");

    private final LanguageLevel myLevel;
    private final char[][] myTable = new char[NUM_ENTRIES][];
    private final IElementType[] myKeywords = new IElementType[NUM_ENTRIES];
    private final Set<String> myKeywordsInSet = new THashSet<String>();

    private void add(String s, IElementType tokenType) {
      char[] chars = s.toCharArray();
      int hashCode = chars[0] * 2;
      for (int j = 1; j < chars.length; j++) {
        hashCode += chars[j];
      }
      int modHashCode = hashCode % NUM_ENTRIES;
      LOG.assertTrue(myTable[modHashCode] == null);

      myTable[modHashCode] = chars;
      myKeywords[modHashCode] = tokenType;
      myKeywordsInSet.add(s);
    }

    public boolean contains(String s) {
      return myKeywordsInSet.contains(s);
    }

    private boolean contains(int hashCode, final char[] bufferArray, final CharSequence buffer, int offset) {
      int modHashCode = hashCode % NUM_ENTRIES;
      final char[] kwd = myTable[modHashCode];
      if (kwd == null) return false;

      if (bufferArray != null) {
        for (int j = 0; j < kwd.length; j++) {
          if (bufferArray[j + offset] != kwd[j]) return false;
        }
      } else {
        for (int j = 0; j < kwd.length; j++) {
          if (buffer.charAt(j + offset) != kwd[j]) return false;
        }
      }
      return true;
    }

    private IElementType getTokenType(int hashCode) {
      return myKeywords[hashCode % NUM_ENTRIES];
    }

    @SuppressWarnings({"HardCodedStringLiteral"})
    private HashTable(final LanguageLevel level) {
      myLevel = level;
      if (level.isAtLeast(LanguageLevel.JDK_1_4)) {
        add("assert", JavaTokenType.ASSERT_KEYWORD);
        if (level.isAtLeast(LanguageLevel.JDK_1_5)) {
          add("enum", JavaTokenType.ENUM_KEYWORD);
        }
      }
      add("abstract", JavaTokenType.ABSTRACT_KEYWORD);
      add("default", JavaTokenType.DEFAULT_KEYWORD);
      add("if", JavaTokenType.IF_KEYWORD);
      add("private", JavaTokenType.PRIVATE_KEYWORD);
      add("this", JavaTokenType.THIS_KEYWORD);
      add("boolean", JavaTokenType.BOOLEAN_KEYWORD);
      add("do", JavaTokenType.DO_KEYWORD);
      add("implements", JavaTokenType.IMPLEMENTS_KEYWORD);
      add("protected", JavaTokenType.PROTECTED_KEYWORD);
      add("throw", JavaTokenType.THROW_KEYWORD);
      add("break", JavaTokenType.BREAK_KEYWORD);
      add("double", JavaTokenType.DOUBLE_KEYWORD);
      add("import", JavaTokenType.IMPORT_KEYWORD);
      add("public", JavaTokenType.PUBLIC_KEYWORD);
      add("throws", JavaTokenType.THROWS_KEYWORD);
      add("byte", JavaTokenType.BYTE_KEYWORD);
      add("else", JavaTokenType.ELSE_KEYWORD);
      add("instanceof", JavaTokenType.INSTANCEOF_KEYWORD);
      add("return", JavaTokenType.RETURN_KEYWORD);
      add("transient", JavaTokenType.TRANSIENT_KEYWORD);
      add("case", JavaTokenType.CASE_KEYWORD);
      add("extends", JavaTokenType.EXTENDS_KEYWORD);
      add("int", JavaTokenType.INT_KEYWORD);
      add("short", JavaTokenType.SHORT_KEYWORD);
      add("try", JavaTokenType.TRY_KEYWORD);
      add("catch", JavaTokenType.CATCH_KEYWORD);
      add("final", JavaTokenType.FINAL_KEYWORD);
      add("interface", JavaTokenType.INTERFACE_KEYWORD);
      add("static", JavaTokenType.STATIC_KEYWORD);
      add("void", JavaTokenType.VOID_KEYWORD);
      add("char", JavaTokenType.CHAR_KEYWORD);
      add("finally", JavaTokenType.FINALLY_KEYWORD);
      add("long", JavaTokenType.LONG_KEYWORD);
      add("strictfp", JavaTokenType.STRICTFP_KEYWORD);
      add("volatile", JavaTokenType.VOLATILE_KEYWORD);
      add("class", JavaTokenType.CLASS_KEYWORD);
      add("float", JavaTokenType.FLOAT_KEYWORD);
      add("native", JavaTokenType.NATIVE_KEYWORD);
      add("super", JavaTokenType.SUPER_KEYWORD);
      add("while", JavaTokenType.WHILE_KEYWORD);
      add("const", JavaTokenType.CONST_KEYWORD);
      add("for", JavaTokenType.FOR_KEYWORD);
      add("new", JavaTokenType.NEW_KEYWORD);
      add("switch", JavaTokenType.SWITCH_KEYWORD);
      add("continue", JavaTokenType.CONTINUE_KEYWORD);
      add("goto", JavaTokenType.GOTO_KEYWORD);
      add("package", JavaTokenType.PACKAGE_KEYWORD);
      add("synchronized", JavaTokenType.SYNCHRONIZED_KEYWORD);
      add("true", JavaTokenType.TRUE_KEYWORD);
      add("false", JavaTokenType.FALSE_KEYWORD);
      add("null", JavaTokenType.NULL_KEYWORD);
    }
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

    final char c = myBufferArray != null ? myBufferArray[myBufferIndex]:myBuffer.charAt(myBufferIndex);
    switch (c) {
      default:
        flexLocateToken();
        break;

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
          final char nextChar = myBufferArray != null ? myBufferArray[myBufferIndex + 1]:myBuffer.charAt(myBufferIndex + 1);

          if (nextChar == '/') {
            myTokenType = JavaTokenType.END_OF_LINE_COMMENT;
            myTokenEndOffset = getLineTerminator(myBufferIndex + 2);
          }
          else if (nextChar == '*') {
            if (myBufferIndex + 2 >= myBufferEndOffset || 
                (myBufferArray != null ? myBufferArray[myBufferIndex + 2]:myBuffer.charAt(myBufferIndex + 2)) != '*' ||
                (myBufferIndex + 3 < myBufferEndOffset && 
                 (myBufferArray != null ? myBufferArray[myBufferIndex + 3]:myBuffer.charAt(myBufferIndex + 3)) == '/')) {
              myTokenType = JavaTokenType.C_STYLE_COMMENT;
              myTokenEndOffset = getClosingComment(myBufferIndex + 2);
            }
            else {
              myTokenType = JavaDocElementType.DOC_COMMENT;
              myTokenEndOffset = getClosingComment(myBufferIndex + 3);
            }
          }
          else if (c > 127 && Character.isJavaIdentifierStart(c)) {
            myTokenEndOffset = getIdentifier(myBufferIndex + 1);
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
    }

    if (myTokenEndOffset > myBufferEndOffset) {
      myTokenEndOffset = myBufferEndOffset;
    }
  }

  private int getWhitespaces(int pos) {
    if (pos >= myBufferEndOffset) return myBufferEndOffset;
    final CharSequence lBuffer = myBuffer;
    final char[] lBufferArray = myBufferArray;

    char c = lBufferArray != null ? lBufferArray[pos]:lBuffer.charAt(pos);

    while (c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\f') {
      pos++;
      if (pos == myBufferEndOffset) return pos;
      c = lBufferArray != null ? lBufferArray[pos]:lBuffer.charAt(pos);
    }

    return pos;
  }

  private void flexLocateToken() {
    try {
      myFlexLexer.goTo(myBufferIndex);
      myTokenType = myFlexLexer.advance();
      myTokenEndOffset = myFlexLexer.getTokenEnd();
    }
    catch (IOException e) {
      // Can't be
    }
  }

  private int getClosingParenthesis(int offset, char c) {
    int pos = offset;
    final int lBufferEnd = myBufferEndOffset;
    if (pos >= lBufferEnd) return lBufferEnd;

    final CharSequence lBuffer = myBuffer;
    final char[] lBufferArray = myBufferArray;
    char cur = lBufferArray != null ? lBufferArray[pos]:lBuffer.charAt(pos);

    while (true) {
      while (cur != c && cur != '\n' && cur != '\r' && cur != '\\') {
        pos++;
        if (pos >= lBufferEnd) return lBufferEnd;
        cur = lBufferArray != null ? lBufferArray[pos]:lBuffer.charAt(pos);
      }

      if (cur == '\\') {
        pos++;
        if (pos >= lBufferEnd) return lBufferEnd;
        cur = lBufferArray != null ? lBufferArray[pos]:lBuffer.charAt(pos);
        if (cur == '\n' || cur == '\r') continue;
        pos++;
        if (pos >= lBufferEnd) return lBufferEnd;
        cur = lBufferArray != null ? lBufferArray[pos]:lBuffer.charAt(pos);
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

    final int lBufferEnd = myBufferEndOffset;
    final CharSequence lBuffer = myBuffer;
    final char[] lBufferArray = myBufferArray;

    while (pos < lBufferEnd - 1) {
      final char c = lBufferArray != null ? lBufferArray[pos]:lBuffer.charAt(pos);

      if (c == '*' && (lBufferArray != null ? lBufferArray[pos + 1]:lBuffer.charAt(pos + 1)) == '/') {
        break;
      }
      pos++;
    }

    return pos + 2;
  }

  private int getLineTerminator(int offset) {
    int pos = offset;
    final int lBufferEnd = myBufferEndOffset;
    final CharSequence lBuffer = myBuffer;
    final char[] lBufferArray = myBufferArray;

    while (pos < lBufferEnd) {
      final char c = lBufferArray != null ? lBufferArray[pos]:lBuffer.charAt(pos);
      if (c == '\r' || c == '\n') break;
      pos++;
    }

    return pos;
  }

  private int getIdentifier(int offset) {
    final CharSequence lBuffer = myBuffer;
    final char[] lBufferArray = myBufferArray;

    int hashCode = (lBufferArray != null ? lBufferArray[offset - 1]:lBuffer.charAt(offset - 1)) * 2;
    final int lBufferEnd = myBufferEndOffset;

    int pos = offset;
    if (pos < lBufferEnd) {
      char c = lBufferArray != null ? lBufferArray[pos]:lBuffer.charAt(pos);

      while (c >= 'a' && c <= 'z' ||
             c >= 'A' && c <= 'Z' ||
             c >= '0' && c <= '9' ||
             c == '_' ||
             c == '$' ||
             c > 127 && Character.isJavaIdentifierPart(c)) {
        pos++;
        hashCode += c;

        if (pos == lBufferEnd) break;
        c = lBufferArray != null ? lBufferArray[pos]:lBuffer.charAt(pos);
      }
    }

    if (myTable.contains(hashCode, lBufferArray, lBuffer, offset - 1)) {
      myTokenType = myTable.getTokenType(hashCode);
    }
    else {
      myTokenType = JavaTokenType.IDENTIFIER;
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
