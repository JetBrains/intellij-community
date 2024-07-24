package com.intellij.codeInspection.tests.kotlin.test

import com.intellij.jvm.analysis.internal.testFramework.test.TestFailedLineInspectionTestBase
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider

abstract class KotlinTestFailedLineInspectionTest : TestFailedLineInspectionTestBase(), KotlinPluginModeProvider {
  fun `test non qualified call`() {
    doTest(
      lang = JvmLanguage.KOTLIN,
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
      lang = JvmLanguage.KOTLIN,
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

  fun `test local method call`() {
    doTest(
      lang = JvmLanguage.KOTLIN,
      text = """
        class LocalFunctionTest {
          @org.junit.jupiter.api.Test
          fun testFoo() {
            fun doTest() {
              org.junit.jupiter.api.Assertions.assertEquals("expected", "actual")
            }
            <warning descr="org.opentest4j.AssertionFailedError:">doTest</warning>()
          }
        }
      """.trimIndent(),
      url = "java:test://LocalFunctionTest/testFoo",
      errorMessage = "org.opentest4j.AssertionFailedError:",
      stackTrace = """
        |${'\t'}at org.junit.jupiter.api.AssertionUtils.fail(AssertionUtils.java:55)
        |${'\t'}at org.junit.jupiter.api.AssertionUtils.failNotEqual(AssertionUtils.java:62)
        |${'\t'}at org.junit.jupiter.api.AssertEquals.assertEquals(AssertEquals.java:182)
        |${'\t'}at org.junit.jupiter.api.AssertEquals.assertEquals(AssertEquals.java:177)
        |${'\t'}at org.junit.jupiter.api.Assertions.assertEquals(Assertions.java:1141)
        |${'\t'}at LocalFunctionTest.testFoo${'$'}doTest(LocalFunctionTest.kt:5)
        |${'\t'}at LocalFunctionTest.testFoo(LocalFunctionTest.kt:7)
      """.trimMargin()
    )
  }
}