package com.intellij.codeInspection.tests.kotlin.test.junit

import com.intellij.codeInspection.tests.ULanguage
import com.intellij.codeInspection.tests.test.junit.JUnit3SuperTearDownInspectionTestBase

class KotlinJUnit3SuperTearDownInspectionTest : JUnit3SuperTearDownInspectionTestBase() {
  fun `test teardown in finally no highlighting`() {
    myFixture.testHighlighting(ULanguage.KOTLIN, """
      class NoProblem : junit.framework.TestCase() {
        override fun tearDown() {
          super.tearDown();
        }
      }
      class CalledInFinally : junit.framework.TestCase() {
        override fun tearDown() {
          try {
            System.out.println()
          } finally {
            super.tearDown()
          }
        }
      }
      class SomeTest : junit.framework.TestCase() {
        override fun setUp() {
          try {
            super.setUp()
          }
          catch (t: Throwable) {
            super.tearDown()
          }
        }
        fun test_something() { }
      }
    """.trimIndent())
  }

  fun `test teardown in finally highlighting`() {
    myFixture.testHighlighting(ULanguage.KOTLIN, """
      class SuperTearDownInFinally : junit.framework.TestCase() {
        override fun tearDown() {
          super.<warning descr="'tearDown()' is not called from 'finally' block">tearDown</warning>()
          System.out.println()
        }
      }      
    """.trimIndent())
  }
}