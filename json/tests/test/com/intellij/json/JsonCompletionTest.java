package com.intellij.json;

import com.intellij.util.ArrayUtil;

/**
 * @author Mikhail Golubev
 */
public class JsonCompletionTest extends JsonTestCase {
  private static final String[] KEYWORDS = new String[]{"true", "false", "null"};
  private static final String[] NOTHING = ArrayUtil.EMPTY_STRING_ARRAY;

  @Override
  protected boolean isCommunity() {
    return true;
  }

  public void testInsideArrayElement1() throws Exception {
    doTest(KEYWORDS);
  }

  public void testInsideArrayElement2() throws Exception {
    doTest(KEYWORDS);
  }

  public void testInsidePropertyKey1() throws Exception {
    doTest(NOTHING);
  }

  public void testInsidePropertyKey2() throws Exception {
    doTest(NOTHING);
  }

  public void testInsidePropertyValue() throws Exception {
    doTest(KEYWORDS);
  }

  private void doTest(String... variants) {
    myFixture.testCompletionVariants("completion/" + getTestName(false) + ".json", variants);
  }
}
