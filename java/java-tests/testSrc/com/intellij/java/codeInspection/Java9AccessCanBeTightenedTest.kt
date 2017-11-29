/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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