package com.intellij.json;

import com.intellij.lexer.Lexer;
import com.intellij.testFramework.LexerTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin.Ulitin
 */
public class JsonLexerTest extends LexerTestCase {
  @Override
  protected @NotNull Lexer createLexer() {
    return new JsonLexer();
  }

  @Override
  protected @NotNull String getDirPath() {
    return null;
  }

  public void testEscapeSlash() {
    // WEB-2803
    doTest("[\"\\/\",-1,\"\\n\", 1]",
           """
             [ ('[')
             DOUBLE_QUOTED_STRING ('"\\/"')
             , (',')
             NUMBER ('-1')
             , (',')
             DOUBLE_QUOTED_STRING ('"\\n"')
             , (',')
             WHITE_SPACE (' ')
             NUMBER ('1')
             ] (']')""");
  }
}
