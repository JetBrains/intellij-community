package com.intellij.json;

import com.intellij.json.psi.JsonElementTypeConverterFactory;

import com.intellij.json.syntax.JsonSyntaxLexer;
import com.intellij.lexer.Lexer;
import com.intellij.platform.syntax.psi.CommonElementTypeConverterFactory;
import com.intellij.platform.syntax.psi.lexer.LexerAdapter;
import com.intellij.testFramework.LexerTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.platform.syntax.psi.ElementTypeConverterKt.compositeElementTypeConverter;

/**
 * @author Konstantin.Ulitin
 */
public class JsonLexerTest extends LexerTestCase {
  @Override
  protected @NotNull Lexer createLexer() {
    return new LexerAdapter(new JsonSyntaxLexer(), compositeElementTypeConverter(List.of(
      new CommonElementTypeConverterFactory().getElementTypeConverter(),
      new JsonElementTypeConverterFactory().getElementTypeConverter())));
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
