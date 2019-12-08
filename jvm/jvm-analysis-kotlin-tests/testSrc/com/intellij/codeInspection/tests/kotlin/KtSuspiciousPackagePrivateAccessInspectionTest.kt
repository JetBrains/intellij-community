package com.intellij.codeInspection.tests.kotlin

import com.intellij.jvm.analysis.JvmAnalysisKtTestsUtil
import com.intellij.testFramework.TestDataPath
import com.siyeh.ig.dependency.SuspiciousPackagePrivateAccessInspectionTestCase

@TestDataPath("/testData/codeInspection/suspiciousPackagePrivateAccess")
class KtSuspiciousPackagePrivateAccessInspectionTest : SuspiciousPackagePrivateAccessInspectionTestCase("kt") {
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

  override fun getBasePath() = "${JvmAnalysisKtTestsUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH}/codeInspection/suspiciousPackagePrivateAccess"
}