package com.intellij.codeInspection.tests

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.codeInspection.InspectionsBundle
import com.intellij.openapi.application.PathManager
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.InspectionTestUtil
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import java.io.File

abstract class UastInspectionTestBase : JavaCodeInsightFixtureTestCase() {
  override fun getTestDataPath(): String = PathManager.getCommunityHomePath().replace(File.separatorChar, '/') + basePath

  abstract val inspection: InspectionProfileEntry

  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(inspection)
  }

  override fun tuneFixture(moduleBuilder: JavaModuleFixtureBuilder<*>) {
    super.tuneFixture(moduleBuilder)
    moduleBuilder.setLanguageLevel(LanguageLevel.JDK_1_8)
    moduleBuilder.addJdk(IdeaTestUtil.getMockJdk18Path().path)
  }

  protected fun JavaCodeInsightTestFixture.testQuickFix(file: String, hint: String) {
    configureByFile(file)
    val action = getAvailableIntention(hint) ?: throw AssertionError("Quickfix '$hint' is not available.")
    launchAction(action)
    checkResultByFile(file.replace(".", ".after."))
  }

  protected fun JavaCodeInsightTestFixture.testQuickFixAll(file: String) {
    val inspection = InspectionTestUtil.instantiateTool(inspection.javaClass) ?: error("No inspection to test.")
    testQuickFix(file, InspectionsBundle.message("fix.all.inspection.problems.in.file", inspection.displayName))
  }

  protected fun JavaCodeInsightTestFixture.testQuickFixUnavailable(file: String, hint: String) {
    configureByFile(file)
    assertEmpty("Quickfix '$hint' is available but should not.", myFixture.filterAvailableIntentions(hint))
  }

  protected fun JavaCodeInsightTestFixture.testQuickFixUnavailableAll(file: String) {
    val inspection = InspectionTestUtil.instantiateTool(inspection.javaClass) ?: error("No inspection to test.")
    testQuickFixUnavailable(file, InspectionsBundle.message("fix.all.inspection.problems.in.file", inspection.displayName))
  }

  override fun tearDown() {
    try {
      myFixture.disableInspections(inspection)
    }
    finally {
      super.tearDown()
    }
  }
}