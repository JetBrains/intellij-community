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
package com.siyeh.ig.junit;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import com.siyeh.ig.testFrameworks.AssertWithoutMessageInspection;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixture.INSPECTION_GADGETS_TEST_DATA_PATH;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

@TestApplication
public class JUnitAssertsWithoutMessagesInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_8);
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new AssertWithoutMessageInspection(), getEnvironmentClasses(),
      INSPECTION_GADGETS_TEST_DATA_PATH + "com/siyeh/igtest/junit/junit_asserts_without_messages");

  @Test
  public void testFailWithMessage() {
    inspectionFixture.get().doTest();
  }

  @Test
  public void testQuickFixAssertEquals() {
    inspectionFixture.get().doTest("""
             import org.junit.Test;
             import static org.junit.Assert.*;
             class TestCase {
                 @Test
                 public void test() {
                     <warning descr="'assertEquals()' without message"><caret>assertEquals</warning>(1, 1);
                 }
             }""");
    inspectionFixture.get().checkQuickFix("Add error message", """
      import org.junit.Test;
      import static org.junit.Assert.*;
      class TestCase {
          @Test
          public void test() {
              assertEquals("<caret>", 1, 1);
          }
      }""");
  }

  @Test
  public void testQuickFixFail() {
    inspectionFixture.get().doTest("""
             import org.junit.Test;
             import static org.junit.Assert.*;
             class TestCase {
                 @Test
                 public void test() {
                     <warning descr="'fail()' without message"><caret>fail</warning>();
                 }
             }""");
    inspectionFixture.get().checkQuickFix("Add error message", """
      import org.junit.Test;
      import static org.junit.Assert.*;
      class TestCase {
          @Test
          public void test() {
              fail("<caret>");
          }
      }""");
  }

  private static String[] getEnvironmentClasses() {
    return new String[] {
      "package org.junit;" +
      "import java.lang.annotation.ElementType;" +
      "import java.lang.annotation.Retention;" +
      "import java.lang.annotation.RetentionPolicy;" +
      "import java.lang.annotation.Target;" +
      "@Retention(RetentionPolicy.RUNTIME)" +
      "@Target({ElementType.METHOD})" +
      "public @interface Test {}",
      "package org.junit;" +
      "public class Assert {" +
      "  static public void assertEquals(double expected, double actual, double delta) {}" +
      "  static public void assertEquals(Object expected, Object actual){}" +
      "  static public void assertEquals(String message, Object expected, Object actual){}" +
      "  static public void assertSame(Object expected, Object actual){}" +
      "  static public void assertSame(String message, Object expected, Object actual){}" +
      "  static public void fail(){}" +
      "  static public void fail(String message){}" +
      "}",

      """
package org.junit.jupiter.api;
public final class Assertions {
    public static void fail(String message) {}
}"""
    };
  }
}