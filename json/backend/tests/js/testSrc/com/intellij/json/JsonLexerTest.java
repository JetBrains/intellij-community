package com.intellij.json;

import com.intellij.json.psi.JsonElementTypeConverterFactory;

import com.intellij.json.syntax.JsonSyntaxLexer;
import com.intellij.lexer.Lexer;
import com.intellij.platform.syntax.psi.CommonElementTypeConverterFactory;
import com.intellij.platform.syntax.psi.lexer.LexerAdapter;
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.LexerTestFixture;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.testFramework.junit5.fixture.TestFixtures;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.platform.syntax.psi.ElementTypeConverterKt.compositeElementTypeConverter;
import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.LexerTestFixtureKt.lexerFixture;

/**
 * @author Konstantin.Ulitin
 */
@TestFixtures
public class JsonLexerTest {
  private final TestFixture<LexerTestFixture> lexer = lexerFixture("", () -> createLexer());

  private static @NotNull Lexer createLexer() {
    return new LexerAdapter(new JsonSyntaxLexer(), compositeElementTypeConverter(List.of(
      new CommonElementTypeConverterFactory().getElementTypeConverter(),
      new JsonElementTypeConverterFactory().getElementTypeConverter())));
  }

  @Test
  public void testEscapeSlash() {
    // WEB-2803
    lexer.get().doTest("[\"\\/\",-1,\"\\n\", 1]",
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
