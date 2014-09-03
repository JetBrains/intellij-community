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

  public void testKeywords() throws Exception {
    doTest();
  }

  public void testNestedArrayLiterals() throws Exception {
    doTest();
  }

  public void testNestedObjectLiterals() throws Exception {
    doTest();
  }

  public void testTopLevelStringLiteral() throws Exception {
    doTest();
  }

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


  public void testComments() {
    doTest();
  }

  public void testStringLiterals() {
    doTest();
  }

  private void doTest() {
    doTest(true);
  }

  @Override
  protected String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath() + "/json/tests/testData";
  }
}
