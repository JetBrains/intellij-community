// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.tests.java.sourceToSink

import com.intellij.codeInspection.tests.sourceToSink.SourceToSinkFlowInspectionTestBase
import com.intellij.jvm.analysis.JavaJvmAnalysisTestUtil
import com.intellij.testFramework.TestDataPath

private const val INSPECTION_PATH = "/codeInspection/sourceToSinkFlow"

@TestDataPath("\$CONTENT_ROOT/testData$INSPECTION_PATH")
class JavaSourceToSinkFlowInspectionTest : SourceToSinkFlowInspectionTestBase() {
  override fun getBasePath(): String {
    return JavaJvmAnalysisTestUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH + INSPECTION_PATH
  }

  fun testSimple() {
    prepareCheckFramework()
    myFixture.testHighlighting("Simple.java")
  }

  fun testLocalInference() {
    prepareCheckFramework()
    myFixture.testHighlighting("LocalInference.java")
  }

  fun testJsrSimple() {
    prepareJsr()
    myFixture.testHighlighting("JsrSimple.java")
  }

  fun testSink() {
    prepareCheckFramework()
    myFixture.testHighlighting("Sink.java")
  }
  fun testSinkJsr() {
    prepareJsr()
    myFixture.testHighlighting("SinkJsr.java")
  }

  fun testCall() {
    prepareCheckFramework()
    myFixture.testHighlighting("Call.java")
  }

  fun testLocalVariables() {
    prepareCheckFramework()
    myFixture.testHighlighting("LocalVariable.java")
  }

  fun testEnumAnnotations() {
    prepareCheckFramework()
    myFixture.testHighlighting("EnumAnnotations.java")
  }
  fun testFields() {
    prepareCheckFramework()
    myFixture.testHighlighting("Fields.java")
  }

  fun testStructure() {
    prepareCheckFramework()
    myFixture.testHighlighting("Structure.java")
  }

  fun testRecursive() {
    prepareCheckFramework()
    myFixture.testHighlighting("Recursive.java")
  }

  fun testMethodPropagation() {
    prepareCheckFramework()
    myFixture.testHighlighting("MethodPropagation.java")
  }

  fun testDifferentExpression() {
    prepareCheckFramework()
    myFixture.testHighlighting("DifferentExpression.java")
  }

  fun `test field with block initializers`() {
    prepareCheckFramework()
    myFixture.testHighlighting("BlockInitializerFields.java")
  }

  fun `test spoiled parameters`() {
    prepareCheckFramework()
    myFixture.testHighlighting("SpoiledParameters.java")
  }

  fun `test drop locality`() {
    prepareCheckFramework()
    myFixture.testHighlighting("DropLocality.java")
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
    myFixture.testHighlighting("Limits.java")
  }
}
