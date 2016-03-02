package com.jetbrains.jsonSchema.impl;

import com.intellij.codeInsight.completion.CompletionTestCase;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.EditorTestUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.io.StringReader;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author Irina.Chernushina on 10/1/2015.
 */
public class JsonBySchemaCompletionTest extends CompletionTestCase {
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

  private void testImpl(@NotNull final String schema, final @NotNull String text,
                        final @NotNull String... variants) throws Exception {
    final int position = EditorTestUtil.getCaretPosition(text);
    Assert.assertTrue(position > 0);
    final String completionText = text.replace("<caret>", "IntelliJIDEARulezzz");

    final PsiFile file = createFile(myModule, "tslint.json", completionText);
    final PsiElement element = file.findElementAt(position);
    Assert.assertNotNull(element);

    final JsonSchemaObject schemaObject = new JsonSchemaReader().read(new StringReader(schema));
    Assert.assertNotNull(schemaObject);

    final List<LookupElement> foundVariants = JsonBySchemaObjectCompletionContributor.getCompletionVariants(schemaObject, element);
    Collections.sort(foundVariants, new Comparator<LookupElement>() {
      @Override
      public int compare(LookupElement o1, LookupElement o2) {
        return o1.getLookupString().compareTo(o2.getLookupString());
      }
    });
    myItems = foundVariants.toArray(new LookupElement[foundVariants.size()]);
    assertStringItems(variants);
  }
}
