package com.intellij.codeInspection.tests.kotlin

import com.intellij.jvm.analysis.KotlinJvmAnalysisTestUtil
import com.intellij.testFramework.TestDataPath
import com.siyeh.ig.dependency.SuspiciousPackagePrivateAccessInspectionTestCase
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider

@TestDataPath("\$CONTENT_ROOT/testData/codeInspection/suspiciousPackagePrivateAccess")
abstract class KotlinSuspiciousPackagePrivateAccessInspectionTest : SuspiciousPackagePrivateAccessInspectionTestCase("kt"), KotlinPluginModeProvider {
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