package com.intellij.codeInspection.tests.kotlin.test

import com.intellij.jvm.analysis.internal.testFramework.test.TestCaseWithoutTestsInspectionTestBase
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider

abstract class KotlinTestCaseWithoutTestsInspectionTest : TestCaseWithoutTestsInspectionTestBase(), KotlinPluginModeProvider {
  fun `test case without test methods`() {
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

  fun `test case with JUnit 3 inner class without test methods`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      class <warning descr="Test class 'TestCaseWithInner' has no tests">TestCaseWithInner</warning> : junit.framework.TestCase() {
        class Inner : junit.framework.TestCase() {
          fun test1() {}
        }
      }
    """.trimIndent())
  }

  fun `test case with JUnit 5 nested class without test methods`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      class <warning descr="Test class 'TestCaseWithInner' has no tests">TestCaseWithInner</warning> {
        @org.junit.jupiter.api.Nested
        inner class <warning descr="Test class 'Inner' has no tests">Inner</warning> {
          private fun test1() { }
        }
      }
    """.trimIndent())
  }

  fun `test case without test methods but class is ignored`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      @org.junit.Ignore
      class IgnoredTest : junit.framework.TestCase() { }
    """.trimIndent())
  }

  fun `test case with test in parent class`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      open class SomeParentClass(val name: String) : junit.framework.TestCase() {
        fun testInParent() { }
      }
      
      class SomeTestClass : SomeParentClass("") { }
    """.trimIndent())
  }
}