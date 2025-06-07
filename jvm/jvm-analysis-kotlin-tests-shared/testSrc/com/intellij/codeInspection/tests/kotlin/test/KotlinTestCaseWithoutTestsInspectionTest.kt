package com.intellij.codeInspection.tests.kotlin.test

import com.intellij.jvm.analysis.internal.testFramework.test.TestCaseWithoutTestsInspectionTestBase
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin

abstract class KotlinTestCaseWithoutTestsInspectionTest : TestCaseWithoutTestsInspectionTestBase(), ExpectedPluginModeProvider {
  override fun setUp() {
    setUpWithKotlinPlugin(testRootDisposable) { super.setUp() }
  }

  fun `test case without test methods in JUnit 3`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      class <warning descr="Test class 'TestCaseWithNoTestMethods' has no tests">TestCaseWithNoTestMethods</warning> : junit.framework.TestCase() {
        override fun setUp() {
          super.setUp()
        }

        override fun tearDown() {
          super.tearDown()
        }

        fun testOne(): Int {
          return 1
        }

        private fun testThree() { }

        fun testFour(i: Int) { i + 1 }
      }
    """.trimIndent())
  }

  fun `test case with inner class without test methods in JUnit 3`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      class <warning descr="Test class 'TestCaseWithInner' has no tests">TestCaseWithInner</warning> : junit.framework.TestCase() {
        class Inner : junit.framework.TestCase() {
          fun test1() {}
        }
      }
    """.trimIndent())
  }

  fun `test case without test methods but class is ignored in JUnit 3`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      @org.junit.Ignore
      class IgnoredTest : junit.framework.TestCase() { }
    """.trimIndent())
  }

  fun `test case with test in parent class in JUnit 3`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      open class SomeParentClass(val name: String) : junit.framework.TestCase() {
        fun testInParent() { }
      }
      
      class SomeTestClass : SomeParentClass("") { }
    """.trimIndent())
  }

  fun `test case with test in sealed parent class in JUnit 3`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      sealed class SomeParentClass(val name: String) : junit.framework.TestCase() {
        fun testInParent() { }
      }
      
      class SomeTestClass : SomeParentClass("") { }
    """.trimIndent())
  }

  fun `test case with test in Java parent class in JUnit 3`() {
    myFixture.addClass("""
      package foo;      
      
      public abstract class SomeParentClass extends junit.framework.TestCase {
        public void testInParent() { }
      }
    """.trimIndent())
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      class SomeTestClass : foo.SomeParentClass() { }
    """.trimIndent())
  }

  fun `test case with test in sealed parent class in JUnit 4`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      sealed class SomeParentClass(val name: String) {
        @org.junit.Test
        fun testInParent() { }
      }
      
      class SomeTestClass : SomeParentClass("") { }
    """.trimIndent())
  }

  fun `test case with test in sealed parent class in JUnit 5`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      sealed class SomeParentClass(val name: String) {
        @org.junit.jupiter.api.Test
        fun testInParent() { }
      }
      
      class SomeTestClass : SomeParentClass("") { }
    """.trimIndent())
  }

  fun `test case with nested class without test methods in JUnit 5`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      class <warning descr="Test class 'TestCaseWithInner' has no tests">TestCaseWithInner</warning> {
        @org.junit.jupiter.api.Nested
        inner class <warning descr="Test class 'Inner' has no tests">Inner</warning> {
          private fun test1() { }
        }
      }
    """.trimIndent())
  }

  fun `test case with single ignored test in TestNG`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      class SomeTest {
        @org.testng.annotations.Test(enabled = false)
        fun foo() { }
      }
    """.trimIndent(), "SomeTest")
  }

  fun `test case with an ignored and non-ignored test in TestNG`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      class SomeTest {
        @org.testng.annotations.Test(enabled = false)
        fun foo() { }
        
        @org.testng.annotations.Test
        fun bar() { }
      }
    """.trimIndent(), "SomeTest")
  }

  fun `test case with test in sealed parent class in TestNG`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      sealed class SomeParentClass(val name: String) {
        @org.testng.annotations.Test
        fun testInParent() { }
      }
      
      class SomeTestClass : SomeParentClass("") { }
    """.trimIndent())
  }
}