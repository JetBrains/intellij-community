// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.tests.java.sourceToSink

import com.intellij.codeInspection.tests.sourceToSink.SourceToSinkFlowInspectionTestBase
import com.intellij.jvm.analysis.JavaJvmAnalysisTestUtil
import com.intellij.testFramework.TestDataPath

private const val inspectionPath = "/codeInspection/sourceToSinkFlow"

@TestDataPath("\$CONTENT_ROOT/testData$inspectionPath")
class JavaSourceToSinkFlowInspectionTest : SourceToSinkFlowInspectionTestBase() {
  override fun getBasePath(): String {
    return JavaJvmAnalysisTestUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH + inspectionPath
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
}
