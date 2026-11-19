// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.tests.java.sourceToSink

import com.intellij.jvm.analysis.JavaJvmAnalysisTestUtil
import com.intellij.jvm.analysis.internal.testFramework.sourceToSink.sourceToSinkFlowInspectionFixture
import com.intellij.jvm.analysis.internal.testFramework.sourceToSink.sourceToSinkFlowProjectFixture
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

private const val INSPECTION_PATH = "/codeInspection/sourceToSinkFlow"

@TestDataPath("\$CONTENT_ROOT/testData$INSPECTION_PATH")
@TestApplication
class JavaSourceToSinkFlowInspectionTest {
  companion object {
    private val projectFixture = sourceToSinkFlowProjectFixture()
  }

  private val myFixture by sourceToSinkFlowInspectionFixture(projectFixture, JavaJvmAnalysisTestUtil.testDataPath + INSPECTION_PATH)

  @Test
  fun testSimple() {
    myFixture.prepareCheckFramework()
    myFixture.testHighlighting("Simple.java")
  }

  @Test
  fun testLocalInference() {
    myFixture.prepareCheckFramework()
    myFixture.testHighlighting("LocalInference.java")
  }

  @Test
  fun testJsrSimple() {
    myFixture.prepareJsr()
    myFixture.testHighlighting("JsrSimple.java")
  }

  @Test
  fun testSink() {
    myFixture.prepareCheckFramework()
    myFixture.testHighlighting("Sink.java")
  }

  @Test
  fun testSinkJsr() {
    myFixture.prepareJsr()
    myFixture.testHighlighting("SinkJsr.java")
  }

  @Test
  fun testCall() {
    myFixture.prepareCheckFramework()
    myFixture.testHighlighting("Call.java")
  }

  @Test
  fun testLocalVariables() {
    myFixture.prepareCheckFramework()
    myFixture.testHighlighting("LocalVariable.java")
  }

  @Test
  fun testEnumAnnotations() {
    myFixture.prepareCheckFramework()
    myFixture.testHighlighting("EnumAnnotations.java")
  }

  @Test
  fun testEnumCallAnnotations() {
    myFixture.prepareCheckFramework()
    myFixture.testHighlighting("EnumCallAnnotations.java")
  }

  @Test
  fun testFields() {
    myFixture.prepareCheckFramework()
    myFixture.testHighlighting("Fields.java")
  }

  @Test
  fun testStructure() {
    myFixture.prepareCheckFramework()
    myFixture.testHighlighting("Structure.java")
  }

  @Test
  fun testRecursive() {
    myFixture.prepareCheckFramework()
    myFixture.testHighlighting("Recursive.java")
  }

  @Test
  fun testMethodPropagation() {
    myFixture.prepareCheckFramework()
    myFixture.testHighlighting("MethodPropagation.java")
  }

  @Test
  fun testDifferentExpression() {
    myFixture.prepareCheckFramework()
    myFixture.testHighlighting("DifferentExpression.java")
  }

  @Test
  fun `test field with block initializers`() {
    myFixture.prepareCheckFramework()
    myFixture.testHighlighting("BlockInitializerFields.java")
  }

  @Test
  fun `test spoiled parameters`() {
    myFixture.prepareCheckFramework()
    myFixture.testHighlighting("SpoiledParameters.java")
  }

  @Test
  fun `test drop locality`() {
    myFixture.prepareCheckFramework()
    myFixture.testHighlighting("DropLocality.java")
  }

  @Test
  fun `test propagation static method`() {
    myFixture.prepareCheckFramework()
    myFixture.testHighlighting("StaticPropagation.java")
  }

  @Test
  fun `test forEachLoop`() {
    myFixture.prepareCheckFramework()
    myFixture.testHighlighting("ForEachLoop.java")
  }
  @Test
  fun `test if statements`() {
    myFixture.prepareCheckFramework()
    myFixture.testHighlighting("IfStatement.java")
  }

  @Test
  fun `test if statements with constants`() {
    myFixture.prepareCheckFramework()
    myFixture.testHighlighting("IfStatementConstant.java")
  }

  @Test
  fun `test if statements with non constants method`() {
    myFixture.prepareCheckFramework()
    myFixture.testHighlighting("IfStatementNonConstantMethod.java")
  }

  @Test
  fun `test if expressions`() {
    myFixture.prepareCheckFramework()
    myFixture.testHighlighting("IfExpressions.java")
  }

  @Test
  fun `test if expressions with constants`() {
    myFixture.prepareCheckFramework()
    myFixture.testHighlighting("IfExpressionConstant.java")
  }

  @Test
  fun `test if statements in method with constants`() {
    myFixture.prepareCheckFramework()
    myFixture.testHighlighting("IfStatementConstantMethod.java")
  }

  @Test
  fun `test lambdaWithForEachLoop`() {
    myFixture.prepareCheckFramework()
    myFixture.testHighlighting("LambdaWithForEachLoop.java")
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
    myFixture.testHighlighting("Limits.java")
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

    myFixture.testHighlighting("FromMethod.java")
  }

  @Test
  fun `test custom inheritance`() {
    myFixture.prepareCheckFramework()
    myFixture.inspection.setTaintedMethod("com.test.Random", "nextInt")
    myFixture.inspection.setUntaintedMethod("com.test.SecureRandom", "nextInt")

    myFixture.testHighlighting("SecureCustomInheritance.java")
  }
}
