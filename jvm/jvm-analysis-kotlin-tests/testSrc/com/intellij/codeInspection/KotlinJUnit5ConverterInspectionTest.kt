// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.codeInspection.tests.JUnit5ConverterInspectionTestBase
import com.intellij.jvm.analysis.JvmAnalysisKtTestsUtil

class KotlinJUnit5ConverterInspectionTest : JUnit5ConverterInspectionTestBase() {
  override val fileExt: String = "kt"

  override fun getBasePath() = JvmAnalysisKtTestsUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH + "/codeInspection/junit5converter"

  fun `test qualified conversion`() {
    doConversionTest("Qualified")
  }

  fun `test unqualified conversion`() {
    doConversionTest("UnQualified")
  }

  fun `test expected on test annotation`() {
    doQfUnavailableTest("ExpectedOnTestAnnotation", JvmAnalysisBundle.message("jvm.inspections.junit5.converter.quickfix"))
  }
}