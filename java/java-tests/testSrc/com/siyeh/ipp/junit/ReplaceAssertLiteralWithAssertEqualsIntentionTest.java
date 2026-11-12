// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.junit;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ipp.IPPFixtureKt.ippFixture;

/**
 * @see com.siyeh.ipp.junit.ReplaceAssertLiteralWithAssertEqualsIntention
 * @author Bas Leijdekkers
 */
@TestApplication
public class ReplaceAssertLiteralWithAssertEqualsIntentionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<IPPFixture> ipp =
    ippFixture(projectFixture, "junit/replace_assert_literal_with_assert_equals", IntentionPowerPackBundle.message("replace.assert.literal.with.assert.equals.intention.name", "assertTrue", "true"));

  @Test
  public void testOutsideTestMethod() { ipp.get().doTest(); }
  @Test
  public void testJUnit5Test() { ipp.get().doTest(); }

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = ipp.get().getFixture();
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
}
