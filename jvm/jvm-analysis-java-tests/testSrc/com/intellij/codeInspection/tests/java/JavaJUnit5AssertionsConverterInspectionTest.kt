// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.tests.java

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.codeInspection.tests.JUnit5AssertionsConverterInspectionTestBase
import com.intellij.jvm.analysis.JvmAnalysisTestsUtil
import com.siyeh.ig.junit.JUnitCommonClassNames

class JavaJUnit5AssertionsConverterInspectionTest : JUnit5AssertionsConverterInspectionTestBase() {
  override val fileExt: String = "java"

  override fun getTestDataPath() = JvmAnalysisTestsUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH + "/codeInspection/junit5assertionsconverter"

  fun `test AssertArrayEquals`() =
    doAssertionTest("AssertArrayEquals", JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_ASSERTIONS)

  fun `test AssertArrayEquals message`() =
    doAssertionTest("AssertArrayEqualsMessage", JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_ASSERTIONS)

  fun `test AssertEquals`() = doAssertionTest("AssertEquals", JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_ASSERTIONS)

  fun `test AssertNotEqualsWithDelta`() = doQfUnavailableTest(
    "AssertNotEqualsWithDelta",
    JvmAnalysisBundle.message("jvm.inspections.junit5.assertions.converter.quickfix", JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_ASSERTIONS)
  )

  fun `test AssertThat`() = doAssertionTest("AssertThat", JUnitCommonClassNames.ORG_HAMCREST_MATCHER_ASSERT)

  fun `test AssertTrue`() = doAssertionTest("AssertTrue", JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_ASSERTIONS)

  fun `test AssertTrue method reference`() =
    doAssertionTest("AssertTrueMethodRef", JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_ASSERTIONS)

  fun `test AssumeTrue`() = doAssertionTest("AssumeTrue", JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_ASSUMPTIONS)
}