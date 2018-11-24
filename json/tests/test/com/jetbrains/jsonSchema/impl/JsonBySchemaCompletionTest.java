package com.jetbrains.jsonSchema.impl;

import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.jetbrains.jsonSchema.JsonSchemaHighlightingTest;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

/**
 * @author Irina.Chernushina on 10/1/2015.
 */
public class JsonBySchemaCompletionTest extends JsonBySchemaCompletionBaseTest {
  public void testTopLevel() throws Exception {
    testImpl("{\"properties\": {\"prima\": {}, \"proto\": {}, \"primus\": {}}}", "{<caret>}", "\"prima\"", "\"primus\"", "\"proto\"");
  }

  public void testTopLevelVariant() throws Exception {
    testImpl("{\"properties\": {\"prima\": {}, \"proto\": {}, \"primus\": {}}}", "{\"pri<caret>\"}", "prima", "primus", "proto");
  }

  public void testBoolean() throws Exception {
    testImpl("{\"properties\": {\"prop\": {\"type\": \"boolean\"}}}", "{\"prop\": <caret>}", "false", "true");
  }

  public void testEnum() throws Exception {
    testImpl("{\"properties\": {\"prop\": {\"enum\": [\"prima\", \"proto\", \"primus\"]}}}",
             "{\"prop\": <caret>}", "\"prima\"", "\"primus\"", "\"proto\"");
  }

  public void testTopLevelAnyOfValues() throws Exception {
    testImpl("{\"properties\": {\"prop\": {\"anyOf\": [{\"enum\": [\"prima\", \"proto\", \"primus\"]}," +
             "{\"type\": \"boolean\"}]}}}",
             "{\"prop\": <caret>}", "\"prima\"", "\"primus\"", "\"proto\"", "false", "true");
  }

  public void testTopLevelAnyOf() throws Exception {
    testImpl("{\"anyOf\": [ {\"properties\": {\"prima\": {}, \"proto\": {}, \"primus\": {}}}," +
             "{\"properties\": {\"abrakadabra\": {}}}]}",
             "{<caret>}", "\"abrakadabra\"", "\"prima\"", "\"primus\"", "\"proto\"");
  }

  public void testSimpleHierarchy() throws Exception {
    testImpl("{\"properties\": {\"top\": {\"properties\": {\"prima\": {}, \"proto\": {}, \"primus\": {}}}}}",
             "{\"top\": {<caret>}}", "\"prima\"", "\"primus\"", "\"proto\"");
  }

  public void testObjectsInsideArray() throws Exception {
    final String schema = "{\"properties\": {\"prop\": {\"type\": \"array\", \"items\": {\"type\": \"object\"," +
                          "\"properties\": {" +
                          "\"innerType\":{}, \"innerValue\":{}" +
                          "}, \"additionalProperties\": false" +
                          "}}}}";
    testImpl(schema, "{\"prop\": [{<caret>}]}", "\"innerType\"", "\"innerValue\"");
  }

  public void testObjectValuesInsideArray() throws Exception {
    final String schema = "{\"properties\": {\"prop\": {\"type\": \"array\", \"items\": {\"type\": \"object\"," +
                          "\"properties\": {" +
                          "\"innerType\":{\"enum\": [115,117, \"nothing\"]}, \"innerValue\":{}" +
                          "}, \"additionalProperties\": false" +
                          "}}}}";
    testImpl(schema, "{\"prop\": [{\"innerType\": <caret>}]}", "\"nothing\"", "115", "117");
  }

  public void testLowLevelOneOf() throws Exception {
    final String schema = "{\"properties\": {\"prop\": {\"type\": \"array\", \"items\": {\"type\": \"object\"," +
                          "\"properties\": {" +
                          "\"innerType\":{\"oneOf\": [" +
                          "{\"properties\": {\"a1\": {}, \"a2\": {}}}" + "," +
                          "{\"properties\": {\"b1\": {}, \"b2\": {}}}" +
                          "]}, \"innerValue\":{}" +
                          "}, \"additionalProperties\": false" +
                          "}}}}";
    testImpl(schema, "{\"prop\": [{\"innerType\": {<caret>}}]}", "\"a1\"", "\"a2\"", "\"b1\"", "\"b2\"");
  }

  public void testArrayValuesInsideObject() throws Exception {
    final String schema = "{\"properties\": {\"prop\": {\"type\": \"array\"," +
                          "\"items\": {\"enum\": [1,2,3]}}}}";
    testImpl(schema, "{\"prop\": [<caret>]}", "1", "2", "3");
  }

  public void testAllOfTerminal() throws Exception {
    final String schema = "{\"allOf\": [{\"type\": \"object\", \"properties\": {\"first\": {}}}," +
                          " {\"properties\": {\"second\": {\"enum\": [33,44]}}}]}";
    testImpl(schema, "{\"<caret>\"}", "first", "second");
  }

  public void testAllOfInTheMiddle() throws Exception {
    final String schema = "{\"allOf\": [{\"type\": \"object\", \"properties\": {\"first\": {}}}," +
                          " {\"properties\": {\"second\": {\"enum\": [33,44]}}}]}";
    testImpl(schema, "{\"second\": <caret>}", "33", "44");
  }

  public void testValueCompletion() throws Exception {
    final String schema = "{\n" +
                          "  \"properties\": {\n" +
                          "    \"top\": {\n" +
                          "      \"enum\": [\"test\", \"me\"]\n" +
                          "    }\n" +
                          "  }\n" +
                          "}";
    testImpl(schema, "{\"top\": <caret>}", "\"me\"", "\"test\"");
  }

  public void testTopLevelArrayPropNameCompletion() throws Exception {
    final String schema = parcelShopSchema();
    testImpl(schema, "[{<caret>}]", "\"address\"");
    testImpl(schema, "[{\"address\": {<caret>}}]", "\"fax\"", "\"houseNumber\"");
    testImpl(schema, "[{\"address\": {\"houseNumber\": <caret>}}]", "1", "2");
  }

  public void testPatternPropertyCompletion() throws Exception {
    final String schema = "{\n" +
                          "  \"patternProperties\": {\n" +
                          "    \"C\": {\n" +
                          "      \"enum\": [\"test\", \"em\"]\n" +
                          "    }\n" +
                          "  }\n" +
                          "}";
    testImpl(schema, "{\"Cyan\": <caret>}", "\"em\"", "\"test\"");
  }

  public void testRootObjectRedefined() throws Exception {
    testImpl(JsonSchemaHighlightingTest.rootObjectRedefinedSchema(), "{<caret>}",
             "\"r1\"", "\"r2\"");
  }

  public void testSimpleNullCompletion() throws Exception {
    final String schema = "{\n" +
                          "  \"properties\": {\n" +
                          "    \"null\": {\n" +
                          "      \"type\": \"null\"\n" +
                          "    }\n" +
                          "  }\n" +
                          "}";
    testImpl(schema, "{\"null\": <caret>}", "null");
  }

  public void testNullCompletionInEnum() throws Exception {
    final String schema = "{\n" +
                          "  \"properties\": {\n" +
                          "    \"null\": {\n" +
                          "      \"type\": [\"null\", \"integer\"],\n" +
                          "      \"enum\": [null, 1, 2]\n" +
                          "    }\n" +
                          "  }\n" +
                          "}";
    testImpl(schema, "{\"null\": <caret>}", "1", "2", "null");
  }

  public void testNullCompletionInTypeVariants() throws Exception {
    final String schema = "{\n" +
                          "  \"properties\": {\n" +
                          "    \"null\": {\n" +
                          "      \"type\": [\"null\", \"boolean\"]\n" +
                          "    }\n" +
                          "  }\n" +
                          "}";
    testImpl(schema, "{\"null\": <caret>}", "false", "null", "true");
  }

  public void testDescriptionFromDefinitionInCompletion() throws Exception {
    final String schema = "{\n" +
                          "  \"definitions\": {\n" +
                          "    \"target\": {\n" +
                          "      \"description\": \"Target description\"\n" +
                          "    }\n" +
                          "  },\n" +
                          "  \"properties\": {\n" +
                          "    \"source\": {\n" +
                          "      \"$ref\": \"#/definitions/target\"\n" +
                          "    }\n" +
                          "  }\n" +
                          "}";
    testImpl(schema, "{<caret>}", "\"source\"");
    Assert.assertEquals(1, myItems.length);
    final LookupElementPresentation presentation = new LookupElementPresentation();
    myItems[0].renderElement(presentation);
    Assert.assertEquals("Target description", presentation.getTypeText());
  }

  public void testDescriptionFromTitleInCompletion() throws Exception {
    final String schema = "{\n" +
                          "  \"definitions\": {\n" +
                          "    \"target\": {\n" +
                          "      \"title\": \"Target title\",\n" +
                          "      \"description\": \"Target description\"\n" +
                          "    }\n" +
                          "  },\n" +
                          "  \"properties\": {\n" +
                          "    \"source\": {\n" +
                          "      \"$ref\": \"#/definitions/target\"\n" +
                          "    }\n" +
                          "  }\n" +
                          "}";
    testImpl(schema, "{<caret>}", "\"source\"");
    Assert.assertEquals(1, myItems.length);
    final LookupElementPresentation presentation = new LookupElementPresentation();
    myItems[0].renderElement(presentation);
    Assert.assertEquals("Target title", presentation.getTypeText());
  }

  @NotNull
  private static String parcelShopSchema() {
    return "{\n" +
                          "  \"$schema\": \"http://json-schema.org/draft-04/schema#\",\n" +
                          "\n" +
                          "  \"title\": \"parcelshop search response schema\",\n" +
                          "\n" +
                          "  \"definitions\": {\n" +
                          "    \"address\": {\n" +
                          "      \"type\": \"object\",\n" +
                          "      \"properties\": {\n" +
                          "        \"houseNumber\": { \"type\": \"integer\", \"enum\": [1,2]},\n" +
                          "        \"fax\": { \"$ref\": \"#/definitions/phone\" }\n" +
                          "      }\n" +
                          "    },\n" +
                          "    \"phone\": {\n" +
                          "      \"type\": \"object\",\n" +
                          "      \"properties\": {\n" +
                          "        \"countryPrefix\": { \"type\": \"string\" },\n" +
                          "        \"number\": { \"type\": \"string\" }\n" +
                          "      }\n" +
                          "    }\n" +
                          "  },\n" +
                          "\n" +
                          "  \"type\": \"array\",\n" +
                          "\n" +
                          "  \"items\": {\n" +
                          "    \"type\": \"object\",\n" +
                          "    \"properties\": {\n" +
                          "      \"address\": { \"$ref\": \"#/definitions/address\" }\n" +
                          "    }\n" +
                          "  }\n" +
                          "}";
  }

  @SuppressWarnings("TestMethodWithIncorrectSignature")
  private void testImpl(@NotNull final String schema, final @NotNull String text,
                        final @NotNull String... variants) throws Exception {
    testBySchema(schema, text, ".json", variants);
  }
}
