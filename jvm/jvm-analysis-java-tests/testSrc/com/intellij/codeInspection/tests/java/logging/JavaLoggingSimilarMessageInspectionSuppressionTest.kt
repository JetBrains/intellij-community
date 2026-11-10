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
import com.intellij.openapi.application.EDT
import com.intellij.psi.PsiElement
import com.intellij.testFramework.InspectionTestUtil
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.createGlobalContextForTool
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.time.Duration.Companion.minutes

private const val inspectionPath = "/codeInspection/loggingSimilarMessageInspectionSuppression"

@TestDataPath("\$CONTENT_ROOT/testData$inspectionPath")
@TestApplication
class JavaLoggingSimilarMessageInspectionSuppressionTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  @BeforeEach
  fun setUpTestDataPath() {
    myFixture.testDataPath = JavaJvmAnalysisTestUtil.testDataPath + inspectionPath
  }

  @BeforeEach
  fun setUp() {
    addSlf4J(myFixture)
    myFixture.enableInspections(LoggingSimilarMessageInspection())
  }

  @Test
  fun testSuppressedSlf4jStatement(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
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

  @Test
  fun testSuppressedSlf4jMethod(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
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

  @Test
  fun testSuppressedSlf4jClass(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
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

  private suspend fun doSuppressTest() {
    val toolWrapper = GlobalInspectionToolWrapper(object : RedundantSuppressInspection(){
      override fun getInspectionTools(psiElement: PsiElement, profile: InspectionProfile): MutableList<InspectionToolWrapper<*, *>?> {
        val loggingSimilarMessageInspection: LocalInspectionTool = LoggingSimilarMessageInspection()
        return mutableListOf(LocalInspectionToolWrapper(loggingSimilarMessageInspection))
      }
    })
    val scope = AnalysisScope(myFixture.getProject())
    val globalContext =
      createGlobalContextForTool(scope, myFixture.project, mutableListOf<InspectionToolWrapper<*, *>>(toolWrapper))

    withContext(Dispatchers.EDT) {
      InspectionTestUtil.runTool(toolWrapper, scope, globalContext)
      InspectionTestUtil.compareToolResults(globalContext, toolWrapper, false, File(myFixture.getTestDataPath(), myFixture.getTestName(false)).path)
    }
  }
}

