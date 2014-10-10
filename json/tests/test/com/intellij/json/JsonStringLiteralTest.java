package com.intellij.json;

import com.intellij.json.psi.JsonArray;
import com.intellij.json.psi.JsonElementGenerator;
import com.intellij.json.psi.JsonStringLiteral;
import com.intellij.json.psi.JsonValue;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

/**
 * @author Mikhail Golubev
 */
public class JsonStringLiteralTest extends JsonTestCase {

  @NotNull
  private JsonStringLiteral createStringLiteral(@NotNull String literalText) {
    final JsonArray jsonArray = new JsonElementGenerator(myFixture.getProject()).createValue("[\n" + literalText + "\n]");
    assertEquals(1, jsonArray.getValueList().size());
    final JsonValue firstElement = jsonArray.getValueList().get(0);
    assertInstanceOf(firstElement, JsonStringLiteral.class);
    return ((JsonStringLiteral)firstElement);
  }

  private void checkFragments(@NotNull String rawStringLiteral, Object... fragments) {
    final List<Pair<TextRange, String>> actual = createStringLiteral(rawStringLiteral).getTextFragments();
    final List<?> expected = Arrays.asList(fragments);
    assertEquals(expected, actual);
  }

  @NotNull
  private Pair<TextRange, String> fragment(int start, int end, @NotNull String chunk) {
    return Pair.create(new TextRange(start, end), chunk);
  }

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

  public void testFragmentSplittingIllegalEscapes() {
    checkFragments("\"\\q\\zz\\uBEE\\u\\ ",

                   fragment(1, 3, "\\q"),
                   fragment(3, 5, "\\z"),
                   fragment(5, 6, "z"),
                   fragment(6, 11, "\\uBEE"),
                   fragment(11, 13, "\\u"),
                   fragment(13, 15, "\\ "));
  }

  public void testFragmentSplittingUnicodeEscapes() {
    checkFragments("'foo\\uCAFEBABE\\u0027baz'",

                   fragment(1, 4, "foo"),
                   fragment(4, 10, "\\uCAFE"),
                   fragment(10, 14, "BABE"),
                   fragment(14, 20, "\\u0027"),
                   fragment(20, 24, "baz"));
  }
}
