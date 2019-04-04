package com.intellij.json;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.util.ArrayUtil;

/**
 * @author Mikhail Golubev
 */
public class JsonCompletionTest extends JsonTestCase {
  private static final String[] ALL_KEYWORDS = new String[]{"true", "false", "null"};
  private static final String[] NOTHING = ArrayUtil.EMPTY_STRING_ARRAY;

  private void doTest(String... variants) {
    myFixture.testCompletionVariants("completion/" + getTestName(false) + ".json", variants);
  }

  private void doTestSingleVariant() {
    myFixture.configureByFile("completion/" + getTestName(false) + ".json");
    final LookupElement[] variants = myFixture.completeBasic();
    assertNull(variants);
    myFixture.checkResultByFile("completion/" + getTestName(false) + "_after.json" );
  }

  public void testInsideArrayElement1() {
    doTest(ALL_KEYWORDS);
  }

  public void testInsideArrayElement2() {
    doTest(ALL_KEYWORDS);
  }

  public void testInsidePropertyKey1() {
    doTest(NOTHING);
  }

  public void testInsidePropertyKey2() {
    doTest(NOTHING);
  }

  public void testInsideStringLiteral1() {
    doTest(NOTHING);
  }

  public void testInsideStringLiteral2() {
    doTest(NOTHING);
  }

  public void testInsidePropertyValue() {
    doTest(ALL_KEYWORDS);
  }

  // Moved from JavaScript

  public void testKeywords() {
    doTestSingleVariant();
  }

  public void testKeywords_2() {
    doTestSingleVariant();
  }
}
