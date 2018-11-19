// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.tests.java

import com.intellij.codeInspection.tests.UnstableApiUsageInspectionTestBase
import com.intellij.jvm.analysis.JvmAnalysisTestsUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH
import com.intellij.testFramework.TestDataPath

@TestDataPath("/testData/codeInspection/unstableApiUsage")
class UnstableApiUsageInspectionTest : UnstableApiUsageInspectionTestBase() {
  override fun getBasePath() = ""

  override fun getTestDataPath() = "${TEST_DATA_PROJECT_RELATIVE_BASE_PATH}/codeInspection/unstableApiUsage"

  fun testInspection() {
    getInspection().myIgnoreInsideImports = false
    myFixture.testHighlighting(true, false, false, "UnstableElementsTest.java")
  }

  fun testIgnoreImports() {
    getInspection().myIgnoreInsideImports = true
    myFixture.testHighlighting(true, false, false, "UnstableElementsIgnoreImportsTest.java")
  }
}
