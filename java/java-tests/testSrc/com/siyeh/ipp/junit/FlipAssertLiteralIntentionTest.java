// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.junit;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.siyeh.ipp.IPPTestCase;

/**
 * @see FlipAssertLiteralIntention
 * @author Bas Leijdekkers
 */
public class FlipAssertLiteralIntentionTest extends IPPTestCase {

  public void testMessage() { doTest(); }
  public void testExistingStaticImport() { doTest(); }
  public void testStaticImportWithoutTestMethod() { doTest(); }
  public void testJUnit5Test() { doTest(); }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.addClass("package org.junit;" +
                       "public class Assert {" +
                       "  public static void assertTrue(java.lang.String message, boolean condition) {}" +
                       "  public static void assertFalse(boolean condition) {}" +
                       "}");
    myFixture.addClass("package org.junit;" +
                       "@Retention(RetentionPolicy.RUNTIME)" +
                       "@Target({ElementType.METHOD})" +
                       "public @interface Test {}");

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
    return "junit/flip_assert_literal";
  }

  @Override
  protected String getIntentionName() {
    return CommonQuickFixBundle.message("fix.replace.x.with.y", "assertTrue()", "assertFalse()");
  }
}
