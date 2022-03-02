// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.tests.kotlin

import com.intellij.codeInspection.tests.JUnit5ConverterInspectionTestBase
import com.intellij.jvm.analysis.KotlinJvmAnalysisTestUtil
import com.intellij.testFramework.TestDataPath

private const val inspectionPath = "/codeInspection/junit5converter"

@TestDataPath("\$CONTENT_ROOT/testData$inspectionPath")
class KotlinJUnit5ConverterInspectionTest9 : JUnit5ConverterInspectionTestBase() {
  override fun getBasePath() = KotlinJvmAnalysisTestUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH + inspectionPath

  fun `test qualified conversion`() {
    myFixture.testQuickFix("Qualified.kt")
  }

  fun `test unqualified conversion`() {
    myFixture.testQuickFix("UnQualified.kt")
  }

  fun `test expected on test annotation`() {
    myFixture.testQuickFixUnavailable("ExpectedOnTestAnnotation.kt")
  }
}