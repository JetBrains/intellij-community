// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection

import com.intellij.jvm.analysis.JvmAnalysisKtTestsUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl

@TestDataPath("/testData/codeInspection/unstableApiUsage")
class KtUnstableApiUsageInspectionTest : UnstableApiUsageInspectionTestBase() {
  override fun getBasePath() = "${TEST_DATA_PROJECT_RELATIVE_BASE_PATH}/codeInspection/unstableApiUsage"

  override fun performAdditionalSetUp() {
    // otherwise assertion in PsiFileImpl ("Access to tree elements not allowed") will not pass
    (myFixture as CodeInsightTestFixtureImpl).setVirtualFileFilter(VirtualFileFilter.NONE)
  }

  fun testInspection() {
    myFixture.testHighlighting("UnstableElementsTest.kt")
  }
}