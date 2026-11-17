package com.intellij.codeInspection.tests.java.test

import com.intellij.jvm.analysis.internal.testFramework.test.testFailedLineInspectionFixture
import com.intellij.jvm.analysis.internal.testFramework.test.testFailedLineProjectFixture
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

@TestApplication
class JavaTestFailedLineInspectionTest {
  companion object {
    private val projectFixture = testFailedLineProjectFixture()
  }

  private val myFixture by testFailedLineInspectionFixture(projectFixture)

  @Test
  fun `test non qualified call`() {
    myFixture.doTest(
      lang = JvmLanguage.JAVA,
      text = """
        public class MainTest extends junit.framework.TestCase {
          public void testFoo() {
            <warning descr="junit.framework.AssertionFailedError:">assertEquals</warning>();
            assertEquals();
          }
  
          public void assertEquals() {}
        }
      """.trimIndent(),
      fileName = "MainTest",
      url = "java:test://MainTest/testFoo",
      errorMessage = "junit.framework.AssertionFailedError:",
      stackTrace = """
        |${'\t'}at junit.framework.Assert.fail(Assert.java:47)
        |${'\t'}at MainTest.assertEquals(Assert.java:207)
        |${'\t'}at MainTest.testFoo(MainTest.java:3)
      """.trimMargin()
    )
  }

  @Test
  fun `test qualified call`() {
    myFixture.doTest(
      lang = JvmLanguage.JAVA,
      text = """
        public class QualifiedTest extends junit.framework.TestCase {
          public void testFoo() {
            QualifiedTest.<warning descr="junit.framework.AssertionFailedError:">assertEquals</warning>();
          }
        
          public static void assertEquals() {}
        }
      """.trimIndent(),
      fileName = "QualifiedTest",
      url = "java:test://QualifiedTest/testFoo",
      errorMessage = "junit.framework.AssertionFailedError:",
      stackTrace = """
        |${'\t'}at junit.framework.Assert.fail(Assert.java:47)
        |${'\t'}at QualifiedTest.assertEquals(Assert.java:207)
        |${'\t'}at QualifiedTest.testFoo(QualifiedTest.java:3)
      """.trimMargin()
    )
  }
}