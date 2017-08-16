package com.intellij.json;

import com.intellij.testFramework.ParsingTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestDataPath;

/**
 * @author Mikhail Golubev
 */
@TestDataPath("$CONTENT_ROOT/testData/psi/")
public class JsonParsingTest extends ParsingTestCase {
  public JsonParsingTest() {
    super("psi", "json", new JsonParserDefinition());
  }

  @Override
  protected String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath() + "/json/tests/testData";
  }

  private void doTest() {
    doTest(true);
  }

  public void testKeywords() {
    doTest();
  }

  public void testNestedArrayLiterals() {
    doTest();
  }

  public void testNestedObjectLiterals() {
    doTest();
  }

  public void testTopLevelStringLiteral() {
    doTest();
  }

  public void testStringLiterals() {
    doTest();
  }

  public void testComments() {
    doTest();
  }

  public void testIncompleteObjectProperties() {
    doTest();
  }

  public void testMissingCommaBetweenArrayElements() {
    doTest();
  }

  public void testMissingCommaBetweenObjectProperties() {
    doTest();
  }

  public void testNonStandardPropertyKeys() {
    doTest();
  }

  public void testTrailingCommas() {
    doTest();
  }

  // WEB-13600
  public void testNumberLiterals() {
    doTest();
  }

  public void testExtendedIdentifierToken() {
    doTest();
  }

  // Moved from JavaScript

  public void testSimple1() {
    doTest();
  }

  public void testSimple2() {
    doTest();
  }

  public void testSimple4() {
    doTest();
  }

  // TODO: ask about these tests
  //public void testSimple3() {
  //  doTest();
  //}
  //
  //public void testReal1() {
  //  doTest();
  //}
  //
  //public void testReal2() {
  //  doTest();

  //}
}
