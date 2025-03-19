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

import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.testFrameworks.SimplifiableAssertionInspection;

public class SimplifiableTestNGAssertionFixTest extends IGQuickFixesTestCase {

  public void testTrueToNullable() { doTest(); }
  public void testTrueToNullableWithMessage() { doTest(); }
  public void testTrueToSameWithMessage() { doTest(); }
  public void testTrueToNotSameWithMessage() { doTest(); }
  public void testTrueToNotNull() { doTest(); }
  public void testTrueToNotNullWithMessage() { doTest(); }
  public void testTrueToEquals() { doTest(); }
  public void testTrueToEqualsConst() { doTest(); }
  public void testTrueToFail() { doTest(); }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new SimplifiableAssertionInspection());
    myRelativePath = "testng/simplifiable_testng_assertion";
    myDefaultHint = InspectionGadgetsBundle.message("simplify.junit.assertion.simplify.quickfix");

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
