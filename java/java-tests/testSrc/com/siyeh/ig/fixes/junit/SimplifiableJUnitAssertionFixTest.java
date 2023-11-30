/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.siyeh.ig.fixes.junit;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.siyeh.ig.LightJavaInspectionTestCase;
import com.siyeh.ig.testFrameworks.SimplifiableAssertionInspection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class SimplifiableJUnitAssertionFixTest extends LightJavaInspectionTestCase {

  public void testJUnit3TestCase() { doQuickFixTest(); }

  public void testJUnit4TestCase() { doQuickFixTest(); }

  public void testIntegerPrimitive() { doQuickFixTest(); }

  public void testBoxedComparisonToEquals() { doQuickFixTest(); }

  public void testBoxedComparisonToEquals1() { doQuickFixTest(); }

  public void testDoublePrimitive() { doQuickFixTest(); }

  public void testEqualsToTrueJUnit5() { doQuickFixTest(); }

  public void testTrueToEqualsJUnit5() { doQuickFixTest(); }

  public void testTrueToEqualsJUnit5Order() { doQuickFixTest(); }

  public void testTrueToEqualsBetweenIncompatibleTypes() { doQuickFixTest(); }

  public void testFalseToNotEqualsJUnit4() { doQuickFixTest(); }

  public void testObjectEqualsToEquals() { doQuickFixTest(); }

  public void testTrueToArrayEquals() { doQuickFixTest(); }

  public void testTrueToArrayEqualsJUnit5() { doQuickFixTest(); }

  public void testNegatedTrue() { doQuickFixTest(); }

  public void testSimplifiableInstanceOf() { doQuickFixTest(); }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return LightJavaCodeInsightFixtureTestCase.JAVA_18;
  }

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/ig/com/siyeh/igfixes/junit/simplifiable_junit_assertion";
  }

  @Override
  protected @Nullable InspectionProfileEntry getInspection() {
    return new SimplifiableAssertionInspection();
  }

  protected void doQuickFixTest() {
    doTest();
    checkQuickFixAll();
  }


  @SuppressWarnings({"deprecation", "NonFinalUtilityClass"})
  @Override
  protected String[] getEnvironmentClasses() {
    return new String[]{
      """
package junit.framework;
    /** @noinspection RedundantThrows*/ public abstract class TestCase extends Assert {
    protected void setUp() throws Exception {}
    protected void tearDown() throws Exception {}
}""",
      """
package junit.framework;
public class Assert {
    public static void assertTrue(String message, boolean condition) {}
    public static void assertTrue(boolean condition) {}
    public static void assertEquals(String message, Object expected, Object actual) {}
    public static void assertEquals(Object expected, Object actual) {}
    public static void assertFalse(String message, boolean condition) {}
    public static void assertFalse(boolean condition) {}
}
""",
      """
package org.junit;
public class Assert {
    public static void assertTrue(boolean condition) {}
    public static void assertTrue(String message, boolean condition) {}
    public static void assertFalse(boolean condition) {}
    public static void assertFalse(String message, boolean condition) {}
    public static void assertEquals(boolean expected, boolean actual) {}
    public static void assertNotEquals(long expected, long actual) {}
    public static void assertArrayEquals(int[] expected, int[] actual) {}
    public static void assertNotEquals(double expected, double actual, double delta) {}
    public static void assertEquals(double expected, double actual, double delta) {}
    @Deprecated public static void assertEquals(double expected, double actual) {}
    public static void assertFalse(String message, boolean condition) {}
}
""",
      """
package org.junit;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Test {}
""",
      """
package java.util.function; public interface Supplier<T> { T get();}
""",
      """
package org.junit.jupiter.api;
import java.util.function.Supplier;
public class Assertions {
    public static void assertEquals(Object expected, Object actual) {}
    public static void assertEquals(Object expected, Object actual, Supplier<String> message) {}
    public static void assertTrue(boolean expected) {}
    public static void assertTrue(boolean expected, String message) {}
    public static void assertTrue(boolean expected, Supplier<String> message) {}
    public static void assertFalse(boolean condition) {}
    public static void assertFalse(boolean condition, String message) {}
    public static void assertFalse(boolean condition, Supplier<String> message) {}
    public static <T> T assertInstanceOf(Class<T> expectedType, Object actualValue) {}
    public static <T> T assertInstanceOf(Class<T> expectedType, Object actualValue, String message) {}
    public static <T> T assertInstanceOf(Class<T> expectedType, Object actualValue, Supplier<String> messageSupplier) {}
    public static void assertArrayEquals(int[] expected, int[] actual, String message) {}
}
""",
      """
package org.testng;
public class Assert {
    public static void assertTrue(boolean condition) {}
    public static void assertTrue(boolean condition, String message) {}
}
"""
    };
  }
}
