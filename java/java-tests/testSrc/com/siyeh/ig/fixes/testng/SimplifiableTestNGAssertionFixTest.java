/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ig.fixes.testng;

import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.testFrameworks.SimplifiableAssertionInspection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

@TestApplication
public class SimplifiableTestNGAssertionFixTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(new SimplifiableAssertionInspection()), "testng/simplifiable_testng_assertion",
                        InspectionGadgetsBundle.message("simplify.junit.assertion.simplify.quickfix"));

  @Test
  public void testTrueToNullable() { ig.get().doTest(); }
  @Test
  public void testTrueToNullableWithMessage() { ig.get().doTest(); }
  @Test
  public void testTrueToSameWithMessage() { ig.get().doTest(); }
  @Test
  public void testTrueToNotSameWithMessage() { ig.get().doTest(); }
  @Test
  public void testTrueToNotNull() { ig.get().doTest(); }
  @Test
  public void testTrueToNotNullWithMessage() { ig.get().doTest(); }
  @Test
  public void testTrueToEquals() { ig.get().doTest(); }
  @Test
  public void testTrueToEqualsConst() { ig.get().doTest(); }
  @Test
  public void testTrueToFail() { ig.get().doTest(); }

  @BeforeEach
  void setUp() {
    JavaCodeInsightTestFixture myFixture = ig.get().getFixture();
    myFixture.addClass("package org.testng;" +
                       "public class Assert {" +
                       "    public static void assertTrue(boolean condition, String message) {}" +
                       "    public static void assertTrue(boolean condition) {}" +
                       "    public static void assertEquals(Object actual, Object expected, String message) {}" +
                       "    public static void assertEquals(Object actual, Object expected) {}" +
                       "    public static void assertFalse(boolean condition, String message) {}" +
                       "    public static void assertFalse(boolean condition) {}" +
                       "}");
  }
}
