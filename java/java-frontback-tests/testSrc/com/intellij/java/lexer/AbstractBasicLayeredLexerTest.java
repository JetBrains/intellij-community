// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.lexer;

import com.intellij.lexer.LayeredLexer;
import com.intellij.lexer.Lexer;
import com.intellij.lexer.LexerBase;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class AbstractBasicLayeredLexerTest extends TestCase {
  public void testInTheMiddle() {
    Lexer lexer = setupLexer("s=\"abc\\ndef\";");

    assertEquals("s", nextToken(lexer));
    assertEquals("=", nextToken(lexer));
    assertEquals("\"abc", nextToken(lexer));
    assertEquals("\\n", nextToken(lexer));
    assertEquals("def\"", nextToken(lexer));
    assertEquals(";", nextToken(lexer));
    assertNull(lexer.getTokenType());
  }

  public void testModification() {
    Lexer lexer = setupLexer("s=\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\";");
    assertEquals("s", nextToken(lexer));
    assertEquals("=", nextToken(lexer));
    assertEquals("\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\"", nextToken(lexer));
    assertEquals(";", nextToken(lexer));
    assertNull(lexer.getTokenType());

    lexer.start(lexer.getBufferSequence(), 2, lexer.getBufferEnd(), (short) 1);
    assertEquals("\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\"", nextToken(lexer));
    assertEquals(";", nextToken(lexer));
    assertNull(lexer.getTokenType());
  }


  public void testInTheAtStartup() {
    Lexer lexer = setupLexer("s=\"\\ndef\";");

    assertEquals("s", nextToken(lexer));
    assertEquals("=", nextToken(lexer));
    assertEquals("\"", nextToken(lexer));
    assertEquals("\\n", nextToken(lexer));
    assertEquals("def\"", nextToken(lexer));
    assertEquals(";", nextToken(lexer));
    assertNull(lexer.getTokenType());
  }

  public void testInTheAtEnd() {
    Lexer lexer = setupLexer("s=\"abc\\n\";");

    assertEquals("s", nextToken(lexer));
    assertEquals("=", nextToken(lexer));
    assertEquals("\"abc", nextToken(lexer));
    assertEquals("\\n", nextToken(lexer));
    assertEquals("\"", nextToken(lexer));
    assertEquals(";", nextToken(lexer));
    assertNull(lexer.getTokenType());
  }

  public void testNonTerminated() {
    Lexer lexer = setupLexer("s=\"abc\\n");

    assertEquals("s", nextToken(lexer));
    assertEquals("=", nextToken(lexer));
    assertEquals("\"abc", nextToken(lexer));
    assertEquals("\\n", nextToken(lexer));
    assertNull(lexer.getTokenType());
  }

  public void testNonTerminated2() {
    Lexer lexer = setupLexer("s=\"abc\\");

    assertEquals("s", nextToken(lexer));
    assertEquals("=", nextToken(lexer));
    assertEquals("\"abc", nextToken(lexer));
    assertEquals("\\", nextToken(lexer));
    assertNull(lexer.getTokenType());
  }

  public void testUnicode() {
    Lexer lexer = setupLexer("s=\"\\uFFFF\";");
    assertEquals("s", nextToken(lexer));
    assertEquals("=", nextToken(lexer));
    assertEquals("\"", nextToken(lexer));
    assertEquals("\\uFFFF", nextToken(lexer));
    assertEquals("\"", nextToken(lexer));
    assertEquals(";", nextToken(lexer));
    assertNull(lexer.getTokenType());
  }

  public void testDelegatesWithBigStates() {
    final LayeredLexer lexer = new LayeredLexer(new SimpleStateSteppingLexer());
    lexer.registerSelfStoppingLayer(new SimpleStateSteppingLexer(),
                                    new IElementType[]{TokenType.CODE_FRAGMENT},
                                    IElementType.EMPTY_ARRAY);

    lexer.start("1234567890123#1234567890123#1234567890123");
    assertLexerSequence(lexer,
      "1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "1", "2", "3",
      "#",
      "1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "1", "2", "3",
      "#",
      "1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "1", "2", "3"
    );
  }

  protected abstract Lexer setupLexer(String text);

  private static void assertLexerSequence(Lexer lexer, String... tokenTexts) {
    for (String tokenText : tokenTexts) {
      assertEquals(tokenText, nextToken(lexer));
    }
    assertNull(lexer.getTokenType());
  }

  private static String nextToken(Lexer lexer) {
    assertNotNull(lexer.getTokenType());
    final String s = lexer.getBufferSequence().subSequence(lexer.getTokenStart(), lexer.getTokenEnd()).toString();
    lexer.advance();
    return s;
  }

  private static class SimpleStateSteppingLexer extends LexerBase {

    private static final char MARK = '#';

    private CharSequence myBuffer;
    private int myStartOffset;
    private int myEndOffset;
    private int myState;
    private int myPos;

    @Override
    public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
      myBuffer = buffer;
      myStartOffset = startOffset;
      myEndOffset = endOffset;
      myState = initialState;

      myPos = myStartOffset;
    }

    @Override
    public int getState() {
      return myState;
    }

    @Nullable
    @Override
    public IElementType getTokenType() {
      if (myPos >= myEndOffset) {
        return null;
      }

      if (findNextMarkIfStoppedOnMarkAndNotAtStart() != myEndOffset) {
        return TokenType.CODE_FRAGMENT;
      }

      return TokenType.BAD_CHARACTER;
    }

    @Override
    public int getTokenStart() {
      return myPos;
    }

    @Override
    public int getTokenEnd() {
      if (myPos == myEndOffset) {
        return myPos;
      }

      final int nextMark = findNextMarkIfStoppedOnMarkAndNotAtStart();
      if (nextMark != myEndOffset) {
        return nextMark + 1;
      }
      return myPos + 1;
    }

    @Override
    public void advance() {
      myPos = getTokenEnd();

      if (myState == 0) {
        myState = 1;
      }
      else {
        myState <<= 1;
      }
    }

    @NotNull
    @Override
    public CharSequence getBufferSequence() {
      return myBuffer;
    }

    @Override
    public int getBufferEnd() {
      return myEndOffset;
    }

    private int findNextMarkIfStoppedOnMarkAndNotAtStart() {
      if (myPos == myStartOffset ||
          myPos < myEndOffset && myBuffer.charAt(myPos) != MARK) {
        return myEndOffset;
      }

      int i = myPos + 1;
      while (i < myEndOffset && myBuffer.charAt(i) != MARK) {
        i++;
      }
      return i;
    }
  }
}
