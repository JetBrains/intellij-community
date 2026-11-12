package com.intellij.json;

import com.intellij.json.psi.JsonArray;
import com.intellij.json.psi.JsonBooleanLiteral;
import com.intellij.json.psi.JsonElementGenerator;
import com.intellij.json.psi.JsonNumberLiteral;
import com.intellij.json.psi.JsonStringLiteral;
import com.intellij.json.psi.JsonValue;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Mikhail Golubev
 */
@TestApplication
public class JsonLiteralApiTest {
  private static final TestFixture<Project> projectFixture = projectFixture();

  public static final String ALL_CHARACTER_ESCAPES = "\"\\/\b\f\n\r\t";

  @NotNull
  private static JsonStringLiteral createStringLiteralFromText(@NotNull String rawText) {
    final JsonArray jsonArray = new JsonElementGenerator(projectFixture.get()).createValue("[\n" + rawText + "\n]");
    assertEquals(1, jsonArray.getValueList().size());
    final JsonValue firstElement = jsonArray.getValueList().get(0);
    assertInstanceOf(JsonStringLiteral.class, firstElement);
    return ((JsonStringLiteral)firstElement);
  }

  @NotNull
  private static JsonStringLiteral createStringLiteralFromContent(@NotNull String unescapedContent) {
    return new JsonElementGenerator(projectFixture.get()).createStringLiteral(unescapedContent);
  }

  private static void checkFragments(@NotNull String rawStringLiteral, Pair<TextRange, String> @NotNull ... fragments) {
    final List<Pair<TextRange, String>> actual = ReadAction.computeBlocking(() -> createStringLiteralFromText(rawStringLiteral).getTextFragments());
    final List<Pair<TextRange, String>> expected = Arrays.asList(fragments);
    assertEquals(expected, actual);
    for (Pair<TextRange, String> fragment : fragments) {
      final TextRange range = fragment.getFirst();
      final String unescaped = range.substring(rawStringLiteral);
      if (!unescaped.contains("\\")) {
        final String escaped = fragment.getSecond();
        assertEquals(escaped, unescaped, String.format("Bad range %s: fragment without escaping differs after decoding", range));
      }
    }
  }

  @NotNull
  private static Pair<TextRange, String> fragment(int start, int end, @NotNull String chunk) {
    return Pair.create(new TextRange(start, end), chunk);
  }

  @Test
  public void testFragmentSplittingSimpleEscapes() {
    checkFragments("\"\\b\\f\\n\\r\\t\\\\\\/\\\"\"",

                   fragment(1, 3, "\b"),
                   fragment(3, 5, "\f"),
                   fragment(5, 7, "\n"),
                   fragment(7, 9, "\r"),
                   fragment(9, 11, "\t"),
                   fragment(11, 13, "\\"),
                   fragment(13, 15, "/"),
                   fragment(15, 17, "\""));
  }

  @Test
  public void testFragmentSplittingIllegalEscapes() {
    checkFragments("\"\\q\\zz\\uBEE\\u\\ ",

                   fragment(1, 3, "\\q"),
                   fragment(3, 5, "\\z"),
                   fragment(5, 6, "z"),
                   fragment(6, 11, "\\uBEE"),
                   fragment(11, 13, "\\u"),
                   fragment(13, 15, "\\ "));
  }

  @Test
  public void testFragmentSplittingUnicodeEscapes() {
    checkFragments("'foo\\uCAFEBABE\\u0027baz'",

                   fragment(1, 4, "foo"),
                   fragment(4, 10, "\\uCAFE"),
                   fragment(10, 14, "BABE"),
                   fragment(14, 20, "\\u0027"),
                   fragment(20, 23, "baz"));
  }

  @Test
  public void testStringLiteralValue() {
    checkStringContent("simple");
    checkStringContent(ALL_CHARACTER_ESCAPES);
    checkStringContent("\u043c\u0435\u0434\u0432\u0435\u0434\u044c");
  }

  private static void checkStringContent(@NotNull String content) {
    assertEquals(content, ReadAction.computeBlocking(() -> createStringLiteralFromContent(content).getValue()));
  }

  @Test
  public void testBooleanLiteralValue() {
    ReadAction.runBlocking(() -> {
      final JsonElementGenerator generator = new JsonElementGenerator(projectFixture.get());
      assertTrue(generator.<JsonBooleanLiteral>createValue("true").getValue());
      assertFalse(generator.<JsonBooleanLiteral>createValue("false").getValue());
    });
  }

  @Test
  public void testNumberLiteralValue() {
    ReadAction.runBlocking(() -> {
      final JsonElementGenerator generator = new JsonElementGenerator(projectFixture.get());
      assertEquals(123.0, generator.<JsonNumberLiteral>createValue("123.0").getValue(), 1e-5);
      assertEquals(0.1, generator.<JsonNumberLiteral>createValue("0.1").getValue(), 1e-5);
      assertEquals(1e+3, generator.<JsonNumberLiteral>createValue("1e3").getValue(), 1e-5);
      assertEquals(1e+3, generator.<JsonNumberLiteral>createValue("1e+3").getValue(), 1e-5);
      assertEquals(1e-3, generator.<JsonNumberLiteral>createValue("1e-3").getValue(), 1e-5);
      assertEquals(1e+3, generator.<JsonNumberLiteral>createValue("1.00e3").getValue(), 1e-5);
      assertEquals(1.23e-3, generator.<JsonNumberLiteral>createValue("1.23e-3").getValue(), 1e-5);
    });
  }
}
