package com.intellij.codeInspection.tests.java.logging

import com.intellij.analysis.AnalysisScope
import com.intellij.codeInspection.InspectionProfile
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.RedundantSuppressInspection
import com.intellij.codeInspection.ex.GlobalInspectionToolWrapper
import com.intellij.codeInspection.ex.InspectionToolWrapper
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper
import com.intellij.codeInspection.logging.LoggingSimilarMessageInspection
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.jvm.analysis.JavaJvmAnalysisTestUtil
import com.intellij.jvm.analysis.internal.testFramework.logging.LoggingTestUtils.addSlf4J
import com.intellij.psi.PsiElement
import com.intellij.testFramework.InspectionTestUtil
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.createGlobalContextForTool
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import java.io.File

private const val inspectionPath = "/codeInspection/loggingSimilarMessageInspectionSuppression"

@TestDataPath("\$CONTENT_ROOT/testData$inspectionPath")
class JavaLoggingSimilarMessageInspectionSuppressionTest : LightJavaCodeInsightFixtureTestCase() {

  override fun setUp() {
    super.setUp()
    addSlf4J(myFixture)
    myFixture.enableInspections(LoggingSimilarMessageInspection())
  }

  fun testSuppressedSlf4jStatement() {
    myFixture.configureByText(JavaFileType.INSTANCE, """
      import org.slf4j.*;
      class Logging {
        private static Logger LOG = LoggerFactory.getLogger(Logging.class);

        private static void request1(String i) {
          LOG.debug("Call successful");
        }

        private static void request2(int i) {
          //noinspection LoggingSimilarMessage
          LOG.debug("Call successful");
        }
     }""".trimIndent())
    myFixture.checkHighlighting()
    doSuppressTest()
  }

  fun testSuppressedSlf4jMethod() {
    myFixture.configureByText(JavaFileType.INSTANCE, """
      import org.slf4j.*;
      class Logging {
        private static Logger LOG = LoggerFactory.getLogger(Logging.class);

        private static void request1(String i) {
          LOG.debug("Call successful");
        }

        @SuppressWarnings("LoggingSimilarMessage")
        public static void test2() {
            LOG.debug("Call successful");
        }
     }""".trimIndent())
    myFixture.checkHighlighting()
    doSuppressTest()
  }

  fun testSuppressedSlf4jClass() {
    myFixture.configureByText(JavaFileType.INSTANCE, """
      import org.slf4j.*;

      @SuppressWarnings("LoggingSimilarMessage")
      class Logging {
        private static Logger LOG = LoggerFactory.getLogger(Logging.class);

        private static void request1(String i) {
          LOG.debug("Call successful");
        }

        public static void test2() {
            LOG.debug("Call successful");
        }
     }""".trimIndent())
    myFixture.checkHighlighting()
    doSuppressTest()
  }


  private fun doSuppressTest() {
    val toolWrapper = GlobalInspectionToolWrapper(object : RedundantSuppressInspection(){
      override fun getInspectionTools(psiElement: PsiElement, profile: InspectionProfile): MutableList<InspectionToolWrapper<*, *>?> {
        val loggingSimilarMessageInspection: LocalInspectionTool = LoggingSimilarMessageInspection()
        return mutableListOf(LocalInspectionToolWrapper(loggingSimilarMessageInspection))
      }
    })
    val scope = AnalysisScope(myFixture.getProject())
    val globalContext =
      createGlobalContextForTool(scope, project, mutableListOf<InspectionToolWrapper<*, *>>(toolWrapper))

    InspectionTestUtil.runTool(toolWrapper, scope, globalContext)
    InspectionTestUtil.compareToolResults(globalContext, toolWrapper, false, File(getTestDataPath(), getTestName(false)).path)
  }

  override fun getBasePath(): String {
    return JavaJvmAnalysisTestUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH + inspectionPath
  }
}

