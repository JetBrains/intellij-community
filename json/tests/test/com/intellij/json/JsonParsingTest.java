package com.intellij.json;

import com.intellij.testFramework.ParsingTestCase;
import com.intellij.testFramework.PlatformTestUtil;

/**
 * @author Mikhail Golubev
 */
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

  private void doTest() {
    doTest(true);
  }

  @Override
  protected String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath() + "/json/tests/testData";
  }
}
