// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.json;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.util.ArrayUtilRt;

/**
 * @author Mikhail Golubev
 */
public class JsonCompletionTest extends JsonTestCase {
  private static final String[] ALL_KEYWORDS = new String[]{"true", "false", "null"};
  private static final String[] NOTHING = ArrayUtilRt.EMPTY_STRING_ARRAY;

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
