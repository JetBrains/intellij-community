// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.tests.java

import com.intellij.codeInspection.tests.JUnit5ConverterInspectionTestBase
import com.intellij.jvm.analysis.JavaJvmAnalysisTestUtil
import com.intellij.testFramework.TestDataPath

private const val inspectionPath = "/codeInspection/junit5converter"

@TestDataPath("\$CONTENT_ROOT/testData$inspectionPath")
class JavaJUnit5ConverterInspectionTest : JUnit5ConverterInspectionTestBase() {
  override fun getBasePath() = JavaJvmAnalysisTestUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH + inspectionPath

  fun `test qualified conversion`() {
    myFixture.testQuickFixAll("Qualified.java")
  }

  fun `test unqualified conversion`() {
    myFixture.testQuickFixAll("UnQualified.java")
  }

  fun `test expected on test annotation`() {
    myFixture.testQuickFixUnavailableAll("ExpectedOnTestAnnotation.java")
  }
}