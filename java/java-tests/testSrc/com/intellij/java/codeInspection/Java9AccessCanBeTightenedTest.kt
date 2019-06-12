// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection

import com.intellij.codeInspection.java19modules.Java9ModuleEntryPoint
import com.intellij.codeInspection.visibility.VisibilityInspection
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase

class Java9AccessCanBeTightenedTest : LightCodeInsightFixtureTestCase() {

  override fun getTestDataPath() = PathManagerEx.getTestDataPath() + "/inspection/java9AccessCanBeTightened/"

  override fun getProjectDescriptor(): LightProjectDescriptor = JAVA_9

  private lateinit var inspection: VisibilityInspection

  override fun setUp() {
    super.setUp()
    inspection = createGlobalTool()

    myFixture.enableInspections(inspection.sharedLocalInspectionTool!!)
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
    val enabled = !testName.endsWith("Off")

    inspection.setEntryPointEnabled(Java9ModuleEntryPoint.ID, enabled)
    addModuleInfo(testDataPath + testName)
    myFixture.configureByFiles("$testName/foo/bar/$className.java")
    myFixture.checkHighlighting()
  }

  private fun addModuleInfo(path: String) {
    val sourceFile = FileUtil.findFirstThatExist("$path/module-info.java")
    val text = String(FileUtil.loadFileText(sourceFile!!))

    myFixture.configureByText("module-info.java", text)
  }

  private fun createGlobalTool() = VisibilityInspection().apply {
    SUGGEST_PRIVATE_FOR_INNERS = true
    SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = true
    SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = true
  }
}