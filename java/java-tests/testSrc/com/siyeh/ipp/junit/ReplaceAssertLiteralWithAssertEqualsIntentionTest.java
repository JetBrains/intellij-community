// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.junit;

import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPTestCase;

/**
 * @see com.siyeh.ipp.junit.ReplaceAssertLiteralWithAssertEqualsIntention
 * @author Bas Leijdekkers
 */
public class ReplaceAssertLiteralWithAssertEqualsIntentionTest extends IPPTestCase {

  public void testOutsideTestMethod() { doTest(); }
  public void testJUnit5Test() { doTest(); }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.addClass("package org.junit;" +
                       "public class Assert {" +
                       "  static public void assertTrue(boolean actual) {}" +
                       "  static public void assertEquals(Object expected, Object actual) {}" +
                       "}");

    myFixture.addClass("package org.junit.jupiter.api;" +
                       "public @interface Test {}");
    myFixture.addClass("package org.junit.platform.commons.annotation;" +
                       "public @interface Testable {}");
    myFixture.addClass("""
                         package org.junit.jupiter.api;
                         public final class Assertions {
                             public static void assertArrayEquals(Object[] expected, Object[] actual) {}
                             public static void assertArrayEquals(Object[] expected, Object[] actual, String message) {}
                             public static void assertEquals(Object expected, Object actual) {}
                             public static void assertTrue(boolean expected) {}
                             public static void assertFalse(boolean expected) {}
                             public static void assertEquals(Object expected, Object actual, String message) {}
                             public static void assertTrue(Object expected, String message) {}
                             public static void fail(String message) {}}""");
  }

  @Override
  protected String getRelativePath() {
    return "junit/replace_assert_literal_with_assert_equals";
  }

  @Override
  protected String getIntentionName() {
    return IntentionPowerPackBundle.message("replace.assert.literal.with.assert.equals.intention.name", "assertTrue", "true");
  }
}
