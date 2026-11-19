package com.intellij.codeInspection.tests.kotlin.sourceToSink

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.jvm.analysis.KotlinJvmAnalysisTestUtil
import com.intellij.jvm.analysis.internal.testFramework.sourceToSink.sourceToSinkFlowInspectionFixture
import com.intellij.jvm.analysis.internal.testFramework.sourceToSink.sourceToSinkFlowProjectFixture
import com.intellij.openapi.application.EDT
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Test

private const val INSPECTION_PATH = "/codeInspection/sourceToSinkFlow"

@TestDataPath("\$CONTENT_ROOT/testData$INSPECTION_PATH")
@TestApplication
abstract class KotlinSourceToSinkFlowInspectionTest {
  companion object {
    private val projectFixture = sourceToSinkFlowProjectFixture()
  }

  protected val myFixture by sourceToSinkFlowInspectionFixture(projectFixture, KotlinJvmAnalysisTestUtil.testDataPath + INSPECTION_PATH)

  @Test
  fun testSimple() {
    myFixture.prepareCheckFramework()
    myFixture.testHighlighting("Simple.kt")
  }

  @Test
  fun testLocalInference() {
    myFixture.prepareCheckFramework()
    myFixture.testHighlighting("LocalInference.kt")
  }

  @Test
  fun testSink() {
    myFixture.prepareCheckFramework()
    myFixture.testHighlighting("Sink.kt")
  }

  @Test
  fun testSinkJsr() {
    myFixture.prepareJsr()
    myFixture.testHighlighting("SinkJsr.kt")
  }

  @Test
  fun testCall() {
    myFixture.prepareCheckFramework()
    myFixture.testHighlighting("Call.kt")
  }

  @Test
  fun testLocalVariable() {
    myFixture.prepareCheckFramework()
    myFixture.testHighlighting("LocalVariables.kt")
  }

  @Test
  fun testParameters() {
    myFixture.prepareCheckFramework()
    myFixture.testHighlighting("Parameters.kt")
  }

  @Test
  fun testEnumAnnotations() {
    myFixture.prepareCheckFramework()
    myFixture.testHighlighting("EnumAnnotations.kt")
  }

  @Test
  fun testFields() {
    myFixture.prepareCheckFramework()
    myFixture.testHighlighting("Fields.kt")
  }

  @Test
  fun testStructure() {
    myFixture.prepareCheckFramework()
    myFixture.testHighlighting("Structure.kt")
  }

  @Test
  fun testMethodPropagation() {
    myFixture.prepareCheckFramework()
    myFixture.testHighlighting("MethodPropagation.kt")
  }

  @Test
  fun testKotlinPropertyPropagateFix(): Unit = timeoutRunBlocking {
    myFixture.prepareCheckFramework()
    withContext(Dispatchers.EDT) {
      myFixture.configureByFile("Property.kt")
      val propagateAction = myFixture.getAvailableIntention(
        JvmAnalysisBundle.message("jvm.inspections.source.unsafe.to.sink.flow.propagate.safe.text"))!!
      myFixture.launchAction(propagateAction)
      myFixture.checkResultByFile("Property.after.kt")
    }
  }

  @Test
  fun testLimits() {
    myFixture.prepareCheckFramework()
    myFixture.addClass("""
         @SuppressWarnings({"FieldMayBeStatic", "StaticNonFinalField", "RedundantSuppression"}) 
        public class Limit2 {

          public final static String fromAnotherFile = "1";
          public final static String fromAnotherFile2 = fromAnotherFile;
          public final static String fromAnotherFile3 = fromMethod();
          public static String fromAnotherFile4 = "1";
          public final String fromAnotherFile5 = "1";
          public final String fromAnotherFile6;

          public Limit2() {
              this.fromAnotherFile6 = "";
          }

          private static String fromMethod() {
              return "null";
          }
      }
    """.trimIndent())
    myFixture.testHighlighting("Limits.kt")
  }

  @Test
  fun testMethodsAsFields() {
    myFixture.prepareCheckFramework()
    myFixture.addClass("""
        public class MethodAsFields {
            private static final String t = "1";

            public String getT() {
                return t;
            }
        }
    """.trimIndent())
    myFixture.testHighlighting("MethodsAsFields.kt")
  }

  @Test
  fun testDifferentExpressions() {
    myFixture.prepareCheckFramework()
    myFixture.testHighlighting("DifferentExpressions.kt")
  }

  @Test
  fun testKotlinConstructorArguments() {
    myFixture.prepareCheckFramework()
    myFixture.testHighlighting("KotlinConstructorArguments.kt")
  }

  @Test
  fun testKotlinParameters() {
    myFixture.prepareCheckFramework()
    myFixture.testHighlighting("KotlinParameters.kt")
  }

  @Test
  fun testDropLocality() {
    myFixture.prepareCheckFramework()
    myFixture.testHighlighting("DropLocality.kt")
  }

  @Test
  fun `test forEachLoop`() {
    myFixture.prepareCheckFramework()
    myFixture.testHighlighting("ForEachLoop.kt")
  }

  @Test
  fun `test lambdaWithForEachLoop`() {
    myFixture.prepareCheckFramework()
    myFixture.testHighlighting("LambdaWithForEachLoop.kt")
  }

  @Test
  fun `test if`() {
    myFixture.prepareCheckFramework()
    myFixture.testHighlighting("ifStatement.kt")
  }

  @Test
  fun `test custom through tables`() {
    myFixture.inspection.untaintedParameterIndex.apply {
      this.clear()
      this.add("1")
    }
    myFixture.inspection.untaintedParameterMethodClass.apply {
      this.clear()
      this.add("FromMethod")
    }
    myFixture.inspection.untaintedParameterMethodName.apply {
      this.clear()
      this.add("sink")
    }

    myFixture.inspection.taintedParameterIndex.apply {
      this.clear()
      this.add("0")
    }
    myFixture.inspection.taintedParameterMethodClass.apply {
      this.clear()
      this.add("FromMethod")
    }
    myFixture.inspection.taintedParameterMethodName.apply {
      this.clear()
      this.add("test")
    }

    myFixture.inspection.setTaintedMethod("java.lang.String", "toString")
    myFixture.inspection.setUntaintedMethod("java.lang.String", "trim")

    myFixture.testHighlighting("FromMethod.kt")
  }
}