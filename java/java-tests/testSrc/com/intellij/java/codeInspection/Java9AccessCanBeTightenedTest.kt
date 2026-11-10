// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection

import com.intellij.JavaTestUtil
import com.intellij.codeInspection.java19modules.Java9ModuleEntryPoint
import com.intellij.codeInspection.visibility.VisibilityInspection
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase.JAVA_9
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
class Java9AccessCanBeTightenedTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(JAVA_9)
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  @BeforeEach
  fun setUpTestDataPath() {
    myFixture.testDataPath = JavaTestUtil.getJavaTestDataPath() + "/inspection/java9AccessCanBeTightened/"
  }

  private lateinit var inspection: VisibilityInspection

  @BeforeEach
  fun setUp() {
    inspection = VisibilityInspection().apply {
      SUGGEST_PRIVATE_FOR_INNERS = true
      SUGGEST_PACKAGE_LOCAL_FOR_TOP_CLASSES = true
      SUGGEST_PACKAGE_LOCAL_FOR_MEMBERS = true
    }
    myFixture.enableInspections(inspection.sharedLocalInspectionTool)
  }

  @Test
  fun testPublicClass(): Unit = doTestClass()
  @Test
  fun testPublicClassOff(): Unit = doTestClass()

  @Test
  fun testExportedPackage(): Unit = doTestClass()
  @Test
  fun testExportedPackageOff(): Unit = doTestClass()

  @Test
  fun testDeclaredService(): Unit = doTestService()
  @Test
  fun testDeclaredServiceOff(): Unit = doTestService()

  @Test
  fun testUsedService(): Unit = doTestService()
  @Test
  fun testUsedServiceOff(): Unit = doTestService()

  private fun doTestClass() = doTest("Public")
  private fun doTestService() = doTest("Service")

  private fun doTest(className: String) {
    val testName = myFixture.getTestName(true)
    inspection.setEntryPointEnabled(Java9ModuleEntryPoint.ID, !testName.endsWith("Off"))
    myFixture.copyFileToProject("${testName}/module-info.java", "module-info.java")
    myFixture.configureByFile("${testName}/foo/bar/${className}.java")
    myFixture.checkHighlighting()
  }
}