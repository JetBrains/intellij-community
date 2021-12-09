package com.intellij.codeInspection.tests.kotlin

import com.intellij.jvm.analysis.KotlinJvmAnalysisTestUtil
import com.intellij.testFramework.TestDataPath
import com.siyeh.ig.dependency.SuspiciousPackagePrivateAccessInspectionTestCase

@TestDataPath("\$CONTENT_ROOT/testData/codeInspection/suspiciousPackagePrivateAccess")
class KtSuspiciousPackagePrivateAccessInspectionTest28 : SuspiciousPackagePrivateAccessInspectionTestCase("kt") {
  fun testAccessingPackagePrivateMembers() {
    doTestWithDependency()
  }

  fun testAccessingProtectedMembers() {
    doTestWithDependency()
  }

  fun testAccessingProtectedMembersFromKotlin() {
    doTestWithDependency()
  }

  fun testOverridePackagePrivateMethod() {
    doTestWithDependency()
  }

  override fun getBasePath() = "${KotlinJvmAnalysisTestUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH}/codeInspection/suspiciousPackagePrivateAccess"
}