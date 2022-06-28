package com.intellij.codeInspection.tests.java

import com.intellij.codeInspection.tests.AssertEqualsBetweenInconvertibleTypesInspectionTestBase
import com.intellij.jvm.analysis.JavaJvmAnalysisTestUtil

class JavaAssertEqualsBetweenInconvertibleTypesInspectionTest : AssertEqualsBetweenInconvertibleTypesInspectionTestBase() {

  override fun getBasePath() = JavaJvmAnalysisTestUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH + "/codeInspection/assert_equals_between_inconvertible_types"

  fun `test AssertEqualsBetweenInconvertibleTypes`() {
    myFixture.testHighlighting("AssertEqualsBetweenInconvertibleTypes.java")
  }

  fun `test AssertEqualsBetweenInconvertibleTypesAssertJ`() {
    myFixture.testHighlighting("AssertEqualsBetweenInconvertibleTypesAssertJ.java")
  }

  fun `test AssertEqualsBetweenInconvertibleTypesJUnit5`() {
    myFixture.testHighlighting("AssertEqualsBetweenInconvertibleTypesJUnit5.java")
  }

  fun `test AssertNotEqualsBetweenInconvertibleTypes`() {
    myFixture.testHighlighting("AssertNotEqualsBetweenInconvertibleTypes.java")
  }

  fun `test AssertNotEqualsBetweenInconvertibleTypesAssertJ`() {
    myFixture.testHighlighting("AssertNotEqualsBetweenInconvertibleTypesAssertJ.java")
  }

  fun `test AssertNotEqualsBetweenInconvertibleTypesJUnit5`() {
    myFixture.testHighlighting("AssertEqualsBetweenInconvertibleTypesJUnit5.java")
  }

  fun `test AssertNotSameBetweenInconvertibleTypes`() {
    myFixture.testHighlighting("AssertNotEqualsBetweenInconvertibleTypes.java")
  }

  fun `test AssertNotSameBetweenInconvertibleTypesAssertJ`() {
    myFixture.testHighlighting("AssertNotEqualsBetweenInconvertibleTypesAssertJ.java")
  }

  fun `test AssertNotSameBetweenInconvertibleTypesJUnit5`() {
    myFixture.testHighlighting("AssertEqualsBetweenInconvertibleTypesJUnit5.java")
  }

  fun `test AssertSameBetweenInconvertibleTypes`() {
    myFixture.testHighlighting("AssertNotEqualsBetweenInconvertibleTypes.java")
  }

  fun `test AssertSameBetweenInconvertibleTypesAssertJ`() {
    myFixture.testHighlighting("AssertNotEqualsBetweenInconvertibleTypesAssertJ.java")
  }

  fun `test AssertSameBetweenInconvertibleTypesJUnit5`() {
    myFixture.testHighlighting("AssertNotEqualsBetweenInconvertibleTypesAssertJ.java")
  }
}
