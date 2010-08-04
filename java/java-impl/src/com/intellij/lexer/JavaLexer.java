/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.lexer;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.TokenType;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.tree.IElementType;
import gnu.trove.THashSet;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Set;

public class JavaLexer extends LexerBase {
  private JavaLexer(boolean isAssertKeywordEnabled, boolean isJDK15) {
    myTable = getTable(isAssertKeywordEnabled, isJDK15);
    myFlexlexer = new _JavaLexer(isAssertKeywordEnabled, isJDK15);
  }

  public JavaLexer(LanguageLevel level) {
    this(level.hasAssertKeyword(), level.hasEnumKeywordAndAutoboxing());
  }

  private static HashTable getTable(boolean isAssertKeywordEnabled, boolean isJDK15) {
    return isAssertKeywordEnabled
              ? isJDK15 ? ourTableWithAssertAndJDK15 : ourTableWithAssert
              : isJDK15 ? ourTableWithJDK15 : ourTableWithoutAssert;
  }

  private static HashTable getTable(LanguageLevel level) {
    return getTable(level.hasAssertKeyword(), level.hasEnumKeywordAndAutoboxing());
  }


  public static boolean isKeyword(String id, LanguageLevel level) {
    return getTable(level).contains(id);
  }

  private CharSequence myBuffer;
  private int myBufferIndex;
  private int myBufferEndOffset;

  private IElementType myTokenType;
  private _JavaLexer myFlexlexer;

  //Positioned after the last symbol of the current token
  private int myTokenEndOffset;

  private static final class HashTable {
    private static final int NUM_ENTRIES = 999;
    private static final Logger LOG = Logger.getInstance("com.intellij.Lexer.JavaLexer");

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

    private boolean contains(int hashCode, final CharSequence buffer, int offset) {
      int modHashCode = hashCode % NUM_ENTRIES;
      final char[] kwd = myTable[modHashCode];
      if (kwd == null) return false;

      for (int j = 0; j < kwd.length; j++) {
        if (buffer.charAt(j + offset) != kwd[j]) return false;
      }
      return true;
    }

    private IElementType getTokenType(int hashCode) {
      return myKeywords[hashCode % NUM_ENTRIES];
    }

    @SuppressWarnings({"HardCodedStringLiteral"})
    private HashTable(boolean isAssertKeywordEnabled, boolean isJDK15) {
      if (isAssertKeywordEnabled) {
        add("assert", JavaTokenType.ASSERT_KEYWORD);
      }
      if (isJDK15) {
        add("enum", JavaTokenType.ENUM_KEYWORD);
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

  private final HashTable myTable;
  private static final HashTable ourTableWithoutAssert = new HashTable(false, false);
  private static final HashTable ourTableWithAssert = new HashTable(true, false);
  private static final HashTable ourTableWithAssertAndJDK15 = new HashTable(true, true);
  private static final HashTable ourTableWithJDK15 = new HashTable(false, true);

  public final void start(CharSequence buffer, int startOffset, int endOffset, int initialState) {
    myBuffer = buffer;
    myBufferIndex = startOffset;
    myBufferEndOffset = endOffset;
    myTokenType = null;
    myTokenEndOffset = startOffset;
    myFlexlexer.reset(myBuffer, startOffset, endOffset, 0);
  }

  public int getState() {
    return 0;
  }

  public final IElementType getTokenType() {
    locateToken();

    return myTokenType;
  }

  public final int getTokenStart() {
    return myBufferIndex;
  }

  public final int getTokenEnd() {
    locateToken();
    return myTokenEndOffset;
  }


  public final void advance() {
    locateToken();
    myTokenType = null;
  }

  protected final void locateToken() {
    if (myTokenType != null) return;
    _locateToken();
  }

  private void _locateToken() {

    if (myTokenEndOffset == myBufferEndOffset) {
      myTokenType = null;
      myBufferIndex = myBufferEndOffset;
      return;
    }

    myBufferIndex = myTokenEndOffset;

    final char c = myBuffer.charAt(myBufferIndex);
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
          final char nextChar = myBuffer.charAt(myBufferIndex + 1);

          if (nextChar == '/') {
            myTokenType = JavaTokenType.END_OF_LINE_COMMENT;
            myTokenEndOffset = getLineTerminator(myBufferIndex + 2);
          }
          else if (nextChar == '*') {
            if (myBufferIndex + 2 >= myBufferEndOffset || myBuffer.charAt(myBufferIndex + 2) != '*') {
              myTokenType = JavaTokenType.C_STYLE_COMMENT;
              myTokenEndOffset = getClosingComment(myBufferIndex + 2);
            }
            else {
              myTokenType = JavaDocElementType.DOC_COMMENT;
              myTokenEndOffset = getDocClosingComment(myBufferIndex + 3);
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
        myTokenEndOffset = getClosingParenthesys(myBufferIndex + 1, c);
    }

    if (myTokenEndOffset > myBufferEndOffset) {
      myTokenEndOffset = myBufferEndOffset;
    }
  }

  private int getWhitespaces(int pos) {
    if (pos >= myBufferEndOffset) return myBufferEndOffset;
    final CharSequence lBuffer = myBuffer;

    char c = lBuffer.charAt(pos);

    while (c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\f') {
      pos++;
      if (pos == myBufferEndOffset) return pos;
      c = lBuffer.charAt(pos);
    }

    return pos;
  }

  private void flexLocateToken() {
    try {
      myFlexlexer.goTo(myBufferIndex);
      myTokenType = myFlexlexer.advance();
      myTokenEndOffset = myFlexlexer.getTokenEnd();
    }
    catch (IOException e) {
      // Can't be
    }
  }


  private int getClosingParenthesys(int offset, char c) {
    int pos = offset;
    final int lBufferEnd = myBufferEndOffset;
    if (pos >= lBufferEnd) return lBufferEnd;

    final CharSequence lBuffer = myBuffer;
    char cur = lBuffer.charAt(pos);

    while (true) {
      while (cur != c && cur != '\n' && cur != '\r' && cur != '\\') {
        pos++;
        if (pos >= lBufferEnd) return lBufferEnd;
        cur = lBuffer.charAt(pos);
      }

      if (cur == '\\') {
        pos++;
        if (pos >= lBufferEnd) return lBufferEnd;
        cur = lBuffer.charAt(pos);
        if (cur == '\n' || cur == '\r') continue;
        pos++;
        if (pos >= lBufferEnd) return lBufferEnd;
        cur = lBuffer.charAt(pos);
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

  private int getDocClosingComment(int offset) {
    final int lBufferEnd = myBufferEndOffset;
    final CharSequence lBuffer = myBuffer;

    if (offset < lBufferEnd && lBuffer.charAt(offset) == '/') {
      return offset + 1;
    }

    int pos = offset;
    while (pos < lBufferEnd - 1) {
      final char c = lBuffer.charAt(pos);

      if (c == '*' && lBuffer.charAt(pos + 1) == '/') {
        break;
      }
      pos++;
    }
    return pos + 2;
  }

  private int getClosingComment(int offset) {
    int pos = offset;

    final int lBufferEnd = myBufferEndOffset;
    final CharSequence lBuffer = myBuffer;

    while (pos < lBufferEnd - 1) {
      final char c = lBuffer.charAt(pos);

      if (c == '*' && lBuffer.charAt(pos + 1) == '/') {
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

    while (pos < lBufferEnd) {
      final char c = lBuffer.charAt(pos);
      if (c == '\r' || c == '\n') break;
      pos++;
    }

    return pos;
  }

  private int getIdentifier(int offset) {
    final CharSequence lBuffer = myBuffer;

    int hashCode = lBuffer.charAt(offset - 1) * 2;
    final int lBufferEnd = myBufferEndOffset;

    int pos = offset;
    if (pos < lBufferEnd) {
      char c = lBuffer.charAt(pos);

      while (c >= 'a' && c <= 'z' ||
             c >= 'A' && c <= 'Z' ||
             c >= '0' && c <= '9' ||
             c == '_' ||
             c == '$' ||
             c > 127 && Character.isJavaIdentifierPart(c)) {
        pos++;
        hashCode += c;

        if (pos == lBufferEnd) break;
        c = lBuffer.charAt(pos);
      }
    }

    if (myTable.contains(hashCode, lBuffer, offset - 1)) {
      myTokenType = myTable.getTokenType(hashCode);
    }
    else {
      myTokenType = JavaTokenType.IDENTIFIER;
    }

    return pos;
  }

  public CharSequence getBufferSequence() {
    return myBuffer;
  }

  public final int getBufferEnd() {
    return myBufferEndOffset;
  }

  public static void main(String[] args) throws IOException {
    File root = new File(args[0]);

    Stats stats = new Stats();
    walk(root, stats);

    System.out.println("Scanned " + stats.files + " files, total of " + stats.lines + " lines in " + (stats.time / 1000000) + " ms.");
    System.out.println("Size:" + stats.bytes);

  }

  private static void lex(File root, Stats stats) throws IOException {
    stats.files++;
    BufferedReader reader = new BufferedReader(new FileReader(root));
    String s;
    StringBuilder buf = new StringBuilder();
    while ((s = reader.readLine()) != null) {
      stats.lines++;
      buf.append(s).append("\n");
    }
    
    stats.bytes += buf.length();

    long start = System.nanoTime();
    lexText(buf);
    stats.time += System.nanoTime() - start;
  }

  private static void lexText(StringBuilder buf) {
    JavaLexer lexer = new JavaLexer(LanguageLevel.JDK_1_5);
    lexer.start(buf);
    while (lexer.getTokenType() != null) {
      lexer.advance();
    }
  }

  private static class Stats {
    public int files;
    public int lines;
    public long time;
    public long bytes;
  }

  private static void walk(File root, Stats stats) throws IOException {
    if (root.isDirectory()) {
      System.out.println("Lexing in " + root.getPath());
      for (File file : root.listFiles()) {
        walk(file, stats);
      }
    }
    else {
      if (root.getName().endsWith(".java")) {
        lex(root, stats);
      }
    }
  }
}
