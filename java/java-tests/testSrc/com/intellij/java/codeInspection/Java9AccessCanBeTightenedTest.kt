// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection

import com.intellij.codeInspection.java19modules.Java9ModuleEntryPoint
import com.intellij.codeInspection.visibility.VisibilityInspection
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

class Java9AccessCanBeTightenedTest : LightJavaCodeInsightFixtureTestCase() {
  override fun getTestDataPath() = PathManagerEx.getTestDataPath() + "/inspection/java9AccessCanBeTightened/"
  override fun getProjectDescriptor() = JAVA_9

  private lateinit var inspection: VisibilityInspection

  override fun setUp() {
    super.setUp()
    inspection = VisibilityInspection().apply {
      SUGGEST_PRIVATE_FOR_INNERS = true
      SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = true
      SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = true
    }
    myFixture.enableInspections(inspection.sharedLocalInspectionTool)
  }

  fun testPublicClass() = doTestClass()
  fun testPublicClassOff() = doTestClass()

  fun testExportedPackage() = doTestClass()
  fun testExportedPackageOff() = doTestClass()

  fun testDeclaredService() = doTestService()
  fun testDeclaredServiceOff() = doTestService()

  fun testUsedService() = doTestService()
  fun testUsedServiceOff() = doTestService()

  private fun doTestClass() = doTest("Public")
  private fun doTestService() = doTest("Service")

  private fun doTest(className: String) {
    val testName = getTestName(true)
    inspection.setEntryPointEnabled(Java9ModuleEntryPoint.ID, !testName.endsWith("Off"))
    myFixture.copyFileToProject("${testName}/module-info.java", "module-info.java")
    myFixture.configureByFile("${testName}/foo/bar/${className}.java")
    myFixture.checkHighlighting()
  }
}