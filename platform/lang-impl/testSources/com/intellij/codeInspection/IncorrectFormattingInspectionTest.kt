// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection

import com.intellij.codeInspection.incorrectFormatting.IncorrectFormattingInspection
import com.intellij.testFramework.PlatformTestUtil.getCommunityPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File


class IncorrectFormattingInspectionTest : BasePlatformTestCase() {

  fun testBodyNormal() = doTest()
  fun testBodyGlobalWarning() = doTest()

  fun testBodyWrongIndents() = doTest(showDetailedWarnings = true)
  fun testBodyExtraSpace() = doTest(showDetailedWarnings = true)
  fun testBodyExtraLine() = doTest(showDetailedWarnings = true)

  fun testInvalidFile() = doTest()

  private fun doTest(showDetailedWarnings: Boolean = false, extension: String = "xml") {
    myFixture.enableInspections(IncorrectFormattingInspection(showDetailedWarnings))
    myFixture.testHighlighting(true, false, true, "${getTestName(true)}.$extension")
  }

  override fun getTestDataPath() =
    getCommunityPath().replace(File.separatorChar, '/') + "/platform/lang-impl/testData/codeInspection/incorrectFormatting/"

}
