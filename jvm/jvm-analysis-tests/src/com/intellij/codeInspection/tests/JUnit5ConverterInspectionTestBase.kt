// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.tests

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.codeInspection.JUnit5ConverterInspection

abstract class JUnit5ConverterInspectionTestBase : UastQfInspectionTestBase(inspection) {
  override fun setUp() {
    super.setUp()
    // JUnit 4
    myFixture.addClass("""
      package org.junit;
      public @interface Test {}
    """.trimIndent())
    myFixture.addClass("""
      package org.hamcrest;
      public interface Matcher<T>{}
    """.trimIndent())
    myFixture.addClass("""
      package org.hamcrest;
      public class MatcherAssert{}
    """.trimIndent())
    myFixture.addClass("""
      package org.junit;
      
      import org.hamcrest.Matcher;
      
      public class Assert {     
        public static void assertArrayEquals(Object[] expecteds, Object[] actuals) {}
        public static void assertArrayEquals(String message, Object[] expecteds, Object[] actuals) {}
        public static void assertTrue(String message, boolean condition) {}
        public static void assertTrue(boolean condition) {}
        public static void assertEquals(String message, Object expected, Object actual) {}
        public static void assertEquals(Object expected, Object actual) {}
        public static void fail() {}
        public static void fail(String message) {}
        public static <T> void assertThat(String reason, T actual, Matcher<? super T> matcher) {}
        public static void assertNotEquals(double unexpected, double actual, double delta) {}
    }
    """.trimIndent())
    myFixture.addClass("""
      package org.junit;
      public class Assume {     
        public static void assumeTrue(boolean b) {}
        public static void assumeTrue(String message, boolean b) {}
      }
    """.trimIndent())

    // JUnit 5
    myFixture.addClass("""
      package org.junit.jupiter.api;
      @org.junit.platform.commons.annotation.Testable
      public @interface Test {}
    """.trimIndent())
    myFixture.addClass("""
      package org.junit.jupiter.api;
      public @interface AfterEach {}
    """.trimIndent())
    myFixture.addClass("""
      package org.junit.jupiter.api;
      public @interface BeforeAll {}
    """.trimIndent())
    myFixture.addClass("""
      package org.junit.jupiter.api;
      public @interface BeforeEach {}
    """.trimIndent())
    myFixture.addClass("""
      package org.junit.jupiter.api;
      public interface RepetitionInfo {}
    """.trimIndent())
    myFixture.addClass("""
      package org.junit.jupiter.api;
      public final class Assertions {
        public static void assertArrayEquals(Object[] expected, Object[] actual) {}
        public static void assertArrayEquals(Object[] expected, Object[] actual, String message) {}
        public static void assertEquals(Object expected, Object actual) {}
        public static void assertTrue(boolean expected) {}
        public static void assertEquals(Object expected, Object actual, String message) {}
        public static void assertTrue(Object expected, String message) {}
        public static void fail(String message) {}
        public static void fail() {}
      }
    """.trimIndent())
    myFixture.addClass("""
      package org.junit.jupiter.api;
      public class Assumptions {
        public static void assumeTrue(boolean b) {}
        public static void assumeTrue(boolean b, String message) {}
      }
    """.trimIndent())
  }

  protected fun doConversionTest(name: String) {
    doQfAvailableTest(name, JvmAnalysisBundle.message("jvm.inspections.junit5.converter.quickfix"))
  }

  companion object {
    private val inspection = JUnit5ConverterInspection()
  }
}