package com.intellij.codeInspection.tests.kotlin.test

import com.intellij.codeInspection.tests.ULanguage
import com.intellij.codeInspection.tests.test.TestFailedLineInspectionTestBase

class KotlinTestFailedLineInspectionTest : TestFailedLineInspectionTestBase() {
  fun `test non qualified call`() {
    doTest(
      lang = ULanguage.KOTLIN,
      text = """
        class MainTest : junit.framework.TestCase() {
          fun testFoo() {
            <warning descr="junit.framework.AssertionFailedError:">assertEquals</warning>()
            assertEquals()
          }
        
          fun assertEquals() {}
        }
      """.trimIndent(),
      url = "java:test://MainTest/testFoo",
      errorMessage = "junit.framework.AssertionFailedError:",
      stackTrace = """
        |${'\t'}at junit.framework.Assert.fail(Assert.java:47)
        |${'\t'}at MainTest.assertEquals(Assert.java:207)
        |${'\t'}at MainTest.testFoo(MainTest.kt:3)
      """.trimMargin()
    )
  }

  fun `test qualified call`() {
    doTest(
      lang = ULanguage.KOTLIN,
      text = """
        class QualifiedTest : junit.framework.TestCase() {
          fun testFoo() {
            Assertions.<warning descr="junit.framework.AssertionFailedError:">assertEquals</warning>()
          }
        
          object Assertions {
            fun assertEquals() {}
          }
        }
      """.trimIndent(),
      url = "java:test://QualifiedTest/testFoo",
      errorMessage = "junit.framework.AssertionFailedError:",
      stackTrace = """
        |${'\t'}at junit.framework.Assert.fail(Assert.java:47)
        |${'\t'}at QualifiedTest.assertEquals(Assert.java:207)
        |${'\t'}at QualifiedTest.testFoo(QualifiedTest.kt:3)
      """.trimMargin()
    )
  }
}