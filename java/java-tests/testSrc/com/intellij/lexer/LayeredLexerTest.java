package com.intellij.lexer;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.tree.IElementType;
import junit.framework.TestCase;

/**
 * @author max
 */
public class LayeredLexerTest extends TestCase {
  public void testInTheMiddle() throws Exception {
    Lexer lexer = setupLexer("s=\"abc\\ndef\";");

    assertEquals("s", nextToken(lexer));
    assertEquals("=", nextToken(lexer));
    assertEquals("\"abc", nextToken(lexer));
    assertEquals("\\n", nextToken(lexer));
    assertEquals("def\"", nextToken(lexer));
    assertEquals(";", nextToken(lexer));
    assertEquals(null, lexer.getTokenType());
  }

  public void testModification() throws Exception {
    Lexer lexer = setupLexer("s=\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\";");
    assertEquals("s", nextToken(lexer));
    assertEquals("=", nextToken(lexer));
    assertEquals("\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\"", nextToken(lexer));
    assertEquals(";", nextToken(lexer));
    assertEquals(null, lexer.getTokenType());

    lexer.start(lexer.getBufferSequence(), 2, lexer.getBufferEnd(), (short) 1);
    assertEquals("\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\"", nextToken(lexer));
    assertEquals(";", nextToken(lexer));
    assertEquals(null, lexer.getTokenType());
  }


  public void testInTheAtStartup() throws Exception {
    Lexer lexer = setupLexer("s=\"\\ndef\";");

    assertEquals("s", nextToken(lexer));
    assertEquals("=", nextToken(lexer));
    assertEquals("\"", nextToken(lexer));
    assertEquals("\\n", nextToken(lexer));
    assertEquals("def\"", nextToken(lexer));
    assertEquals(";", nextToken(lexer));
    assertEquals(null, lexer.getTokenType());
  }

  public void testInTheAtEnd() throws Exception {
    Lexer lexer = setupLexer("s=\"abc\\n\";");

    assertEquals("s", nextToken(lexer));
    assertEquals("=", nextToken(lexer));
    assertEquals("\"abc", nextToken(lexer));
    assertEquals("\\n", nextToken(lexer));
    assertEquals("\"", nextToken(lexer));
    assertEquals(";", nextToken(lexer));
    assertEquals(null, lexer.getTokenType());
  }

  public void testNonTerminated() throws Exception {
    Lexer lexer = setupLexer("s=\"abc\\n");

    assertEquals("s", nextToken(lexer));
    assertEquals("=", nextToken(lexer));
    assertEquals("\"abc", nextToken(lexer));
    assertEquals("\\n", nextToken(lexer));
    assertEquals(null, lexer.getTokenType());
  }

  public void testNonTerminated2() throws Exception {
    Lexer lexer = setupLexer("s=\"abc\\");

    assertEquals("s", nextToken(lexer));
    assertEquals("=", nextToken(lexer));
    assertEquals("\"abc", nextToken(lexer));
    assertEquals("\\", nextToken(lexer));
    assertEquals(null, lexer.getTokenType());
  }

  public void testUnicode() throws Exception {
    Lexer lexer = setupLexer("s=\"\\uFFFF\";");
    assertEquals("s", nextToken(lexer));
    assertEquals("=", nextToken(lexer));
    assertEquals("\"", nextToken(lexer));
    assertEquals("\\uFFFF", nextToken(lexer));
    assertEquals("\"", nextToken(lexer));
    assertEquals(";", nextToken(lexer));
    assertEquals(null, lexer.getTokenType());
  }

  private Lexer setupLexer(String text) {
    LayeredLexer lexer = new LayeredLexer(new JavaLexer(LanguageLevel.JDK_1_3));
    lexer.registerSelfStoppingLayer(new StringLiteralLexer('\"', JavaTokenType.STRING_LITERAL),
                                    new IElementType[]{JavaTokenType.STRING_LITERAL},
                                    IElementType.EMPTY_ARRAY);

    lexer.start(text);
    return lexer;
  }

  private String nextToken(Lexer lexer) {
    assertTrue(lexer.getTokenType() != null);
    final String s = lexer.getBufferSequence().subSequence(lexer.getTokenStart(), lexer.getTokenEnd()).toString();
    lexer.advance();
    return s;
  }
}
