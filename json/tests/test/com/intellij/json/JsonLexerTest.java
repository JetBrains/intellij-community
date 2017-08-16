package com.intellij.json;

import com.intellij.lexer.Lexer;
import com.intellij.testFramework.LexerTestCase;

/**
 * @author Konstantin.Ulitin
 */
public class JsonLexerTest extends LexerTestCase {
  @Override
  protected Lexer createLexer() {
    return new JsonLexer();
  }

  @Override
  protected String getDirPath() {
    return null;
  }

  public void testEscapeSlash() {
    // WEB-2803
    doTest("[\"\\/\",-1,\"\\n\", 1]",
           "[ ('[')\n" +
           "DOUBLE_QUOTED_STRING ('\"\\/\"')\n" +
           ", (',')\n" +
           "NUMBER ('-1')\n" +
           ", (',')\n" +
           "DOUBLE_QUOTED_STRING ('\"\\n\"')\n" +
           ", (',')\n" +
           "WHITE_SPACE (' ')\n" +
           "NUMBER ('1')\n" +
           "] (']')");
  }
}
