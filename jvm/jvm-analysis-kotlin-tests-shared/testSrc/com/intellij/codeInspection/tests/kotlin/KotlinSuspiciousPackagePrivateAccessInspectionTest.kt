package com.intellij.codeInspection.tests.kotlin

import com.intellij.jvm.analysis.KotlinJvmAnalysisTestUtil
import com.intellij.openapi.module.ModuleManager
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.TestDataPath
import com.intellij.util.PathUtil
import com.siyeh.ig.dependency.SuspiciousPackagePrivateAccessInspectionTestCase
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin
import java.io.File

@TestDataPath("\$CONTENT_ROOT/testData/codeInspection/suspiciousPackagePrivateAccess")
abstract class KotlinSuspiciousPackagePrivateAccessInspectionTest : SuspiciousPackagePrivateAccessInspectionTestCase("kt"), ExpectedPluginModeProvider {
  override fun setUp() {
    setUpWithKotlinPlugin(testRootDisposable) { super.setUp() }
    val jar = File(PathUtil.getJarPathForClass(JvmStatic::class.java))
    val modules = ModuleManager.getInstance(project).modules
    for (module in modules) {
      PsiTestUtil.addLibrary(module, "kotlin-stdlib", jar.parent, jar.name)
    }
  }

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