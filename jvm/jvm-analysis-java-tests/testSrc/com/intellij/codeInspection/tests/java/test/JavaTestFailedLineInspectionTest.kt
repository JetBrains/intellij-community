package com.intellij.codeInspection.tests.java.test

import com.intellij.jvm.analysis.internal.testFramework.test.TestFailedLineInspectionTestBase
import com.intellij.jvm.analysis.testFramework.JvmLanguage

class JavaTestFailedLineInspectionTest : TestFailedLineInspectionTestBase() {
  fun `test non qualified call`() {
    doTest(
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

  fun `test qualified call`() {
    doTest(
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