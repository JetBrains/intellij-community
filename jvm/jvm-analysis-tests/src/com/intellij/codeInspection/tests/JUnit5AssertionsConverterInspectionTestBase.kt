// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.tests

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.codeInspection.JUnit5AssertionsConverterInspection
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase

abstract class JUnit5AssertionsConverterInspectionTestBase : JavaCodeInsightFixtureTestCase() {
  abstract val fileExt: String

  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(inspection)

    // JUnit 4
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
      public class Assert {     
        public static void assertArrayEquals(Object[] expecteds, Object[] actuals) {}
        public static void assertArrayEquals(String message, Object[] expecteds, Object[] actuals) {}
        public static void assertTrue(String message, boolean condition) {}
        public static void assertTrue(boolean condition) {}
        public static void assertEquals(String message, Object expected, Object actual) {}
        public static void assertEquals(Object expected, Object actual) {}
        public static void fail() {}
        public static void fail(String message) {}
        public static <T> void assertThat(String reason, T actual, org.hamcrest.Matcher<? super T> matcher) {}
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

  override fun tuneFixture(moduleBuilder: JavaModuleFixtureBuilder<*>) {
    super.tuneFixture(moduleBuilder)
    moduleBuilder.setLanguageLevel(LanguageLevel.JDK_1_8)
    moduleBuilder.addJdk(IdeaTestUtil.getMockJdk18Path().path)
  }

  protected fun doAssertionTest(name: String, className: String) {
    doQfAvailableTest(name, JvmAnalysisBundle.message("jvm.inspections.junit5.assertions.converter.quickfix", className))
  }

  protected fun doQfAvailableTest(name: String, hint: String) {
    myFixture.configureByFile("$name.$fileExt")
    val action = myFixture.getAvailableIntention(hint) ?: throw AssertionError("Quickfix '$hint' is not available.")
    myFixture.launchAction(action)
    myFixture.checkResultByFile("$name.after.$fileExt")
  }

  protected fun doQfUnavailableTest(name: String, hint: String) {
    myFixture.configureByFile("$name.$fileExt")
    assertEmpty("Quickfix '$hint' is available but should not.", myFixture.filterAvailableIntentions(hint))
  }

  override fun tearDown() {
    try {
      myFixture.disableInspections(inspection)
    }
    finally {
      super.tearDown()
    }
  }

  companion object {
    private val inspection = JUnit5AssertionsConverterInspection()
  }
}