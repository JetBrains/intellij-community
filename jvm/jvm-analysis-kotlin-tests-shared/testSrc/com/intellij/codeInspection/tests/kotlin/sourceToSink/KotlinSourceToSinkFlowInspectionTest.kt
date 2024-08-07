package com.intellij.codeInspection.tests.kotlin.sourceToSink

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.jvm.analysis.KotlinJvmAnalysisTestUtil
import com.intellij.jvm.analysis.internal.testFramework.sourceToSink.SourceToSinkFlowInspectionTestBase
import com.intellij.testFramework.TestDataPath
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider

private const val INSPECTION_PATH = "/codeInspection/sourceToSinkFlow"

@TestDataPath("\$CONTENT_ROOT/testData$INSPECTION_PATH")
abstract class KotlinSourceToSinkFlowInspectionTest : SourceToSinkFlowInspectionTestBase(), KotlinPluginModeProvider {
  override fun getBasePath() = KotlinJvmAnalysisTestUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH + INSPECTION_PATH

  fun testSimple() {
    prepareCheckFramework()
    myFixture.testHighlighting("Simple.kt")
  }

  fun testLocalInference() {
    prepareCheckFramework()
    myFixture.testHighlighting("LocalInference.kt")
  }

  fun testSink() {
    prepareCheckFramework()
    myFixture.testHighlighting("Sink.kt")
  }

  fun testSinkJsr() {
    prepareJsr()
    myFixture.testHighlighting("SinkJsr.kt")
  }

  fun testCall() {
    prepareCheckFramework()
    myFixture.testHighlighting("Call.kt")
  }

  fun testLocalVariable() {
    prepareCheckFramework()
    myFixture.testHighlighting("LocalVariables.kt")
  }

  fun testParameters() {
    prepareCheckFramework()
    myFixture.testHighlighting("Parameters.kt")
  }

  fun testEnumAnnotations() {
    prepareCheckFramework()
    myFixture.testHighlighting("EnumAnnotations.kt")
  }

  fun testFields() {
    prepareCheckFramework()
    myFixture.testHighlighting("Fields.kt")
  }

  fun testStructure() {
    prepareCheckFramework()
    myFixture.testHighlighting("Structure.kt")
  }

  fun testMethodPropagation() {
    prepareCheckFramework()
    myFixture.testHighlighting("MethodPropagation.kt")
  }

  fun testKotlinPropertyPropagateFix() {
    prepareCheckFramework()
    myFixture.configureByFile("Property.kt")
    val propagateAction = myFixture.getAvailableIntention(
      JvmAnalysisBundle.message("jvm.inspections.source.unsafe.to.sink.flow.propagate.safe.text"))!!
    myFixture.launchAction(propagateAction)
    myFixture.checkResultByFile("Property.after.kt")
  }

  fun testLimits() {
    prepareCheckFramework()
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

  fun testMethodsAsFields() {
    prepareCheckFramework()
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

  fun testDifferentExpressions() {
    prepareCheckFramework()
    myFixture.testHighlighting("DifferentExpressions.kt")
  }

  fun testKotlinConstructorArguments() {
    prepareCheckFramework()
    myFixture.testHighlighting("KotlinConstructorArguments.kt")
  }

  fun testKotlinParameters() {
    prepareCheckFramework()
    myFixture.testHighlighting("KotlinParameters.kt")
  }

  fun testDropLocality() {
    prepareCheckFramework()
    myFixture.testHighlighting("DropLocality.kt")
  }

  fun `test forEachLoop`() {
    prepareCheckFramework()
    myFixture.testHighlighting("ForEachLoop.kt")
  }

  fun `test lambdaWithForEachLoop`() {
    prepareCheckFramework()
    myFixture.testHighlighting("LambdaWithForEachLoop.kt")
  }

  fun `test if`() {
    prepareCheckFramework()
    myFixture.testHighlighting("ifStatement.kt")
  }

  fun `test custom through tables`() {
    inspection.untaintedParameterIndex.apply {
      this.clear()
      this.add("1")
    }
    inspection.untaintedParameterMethodClass.apply {
      this.clear()
      this.add("FromMethod")
    }
    inspection.untaintedParameterMethodName.apply {
      this.clear()
      this.add("sink")
    }

    inspection.taintedParameterIndex.apply {
      this.clear()
      this.add("0")
    }
    inspection.taintedParameterMethodClass.apply {
      this.clear()
      this.add("FromMethod")
    }
    inspection.taintedParameterMethodName.apply {
      this.clear()
      this.add("test")
    }

    inspection.setTaintedMethod("java.lang.String", "toString")
    inspection.setUntaintedMethod("java.lang.String", "trim")

    myFixture.testHighlighting("FromMethod.kt")
  }
}