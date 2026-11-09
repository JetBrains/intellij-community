/*
 * Copyright 2000-2023 JetBrains s.r.o.
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
package com.siyeh.ig.junit;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import com.siyeh.ig.testFrameworks.SimplifiableAssertionInspection;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixture.INSPECTION_GADGETS_TEST_DATA_PATH;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class SimplifiableJUnitAssertionInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_8);
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new SimplifiableAssertionInspection(), getEnvironmentClasses(),
      INSPECTION_GADGETS_TEST_DATA_PATH + "com/siyeh/igtest/junit/simplifiable_junit_assertion");

  @Test
  public void testSimplifiableJUnitAssertion() {
    inspectionFixture.get().doTest();
  }

  @Test
  public void testSimplifiableJUnit40Assertion() {
    inspectionFixture.get().doTest();
  }
  @Test
  public void testSimplifiableInstanceOf() {
    inspectionFixture.get().doTest();
  }

  @SuppressWarnings({"deprecation", "NonFinalUtilityClass"})
  private static String[] getEnvironmentClasses() {
    return new String[] {
      """
package junit.framework;
 /** @noinspection ALL*/ public abstract class TestCase extends Assert {
    protected void setUp() throws Exception {}
    protected void tearDown() throws Exception {}
    public static void assertTrue(boolean condition) {
        Assert.assertTrue(condition);
    }
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
}""",

      """
package org.junit;
public class Assert {
    public static void assertTrue(boolean condition) {}
    public static void assertFalse(boolean condition) {}
    public static void assertEquals(boolean expected, boolean actual) {}
    public static void assertFalse(String message, boolean condition) {}
    public static void assertNotEquals(long a, long b) {}
}""",

      """
package org.junit;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Test {}""",

      """
package org.junit.jupiter.api;
import java.util.function.Supplier;
public class Assertions {
    public static void assertNotEquals(Object expected, Object actual) {}
    public static void assertFalse(boolean expected) {}
    public static void assertFalse(boolean condition, String message) {}
    public static void assertFalse(boolean condition, Supplier<String> messageSupplier) {}
    public static void assertTrue(boolean condition) {}
    public static void assertTrue(boolean condition, String message) {}
    public static void assertTrue(boolean condition, Supplier<String> messageSupplier) {}
    public static <T> T assertInstanceOf(Class<T> expectedType, Object actualValue) {}
    public static <T> T assertInstanceOf(Class<T> expectedType, Object actualValue, String message) {}
    public static <T> T assertInstanceOf(Class<T> expectedType, Object actualValue, Supplier<String> messageSupplier) {}
}""",

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