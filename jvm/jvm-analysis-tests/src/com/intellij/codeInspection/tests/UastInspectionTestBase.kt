package com.intellij.codeInspection.tests

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.codeInspection.InspectionsBundle
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.InspectionTestUtil
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase

abstract class UastInspectionTestBase : JavaCodeInsightFixtureTestCase() {
  abstract val fileExt: String

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

  protected fun testHighlighting(vararg names: String) {
    myFixture.testHighlighting(*names.map { "$it.$fileExt" }.toTypedArray())
  }

  protected fun testQuickFix(name: String, hint: String) {
    myFixture.configureByFile("$name.$fileExt")
    val action = myFixture.getAvailableIntention(hint) ?: throw AssertionError("Quickfix '$hint' is not available.")
    myFixture.launchAction(action)
    myFixture.checkResultByFile("$name.after.$fileExt")
  }

  protected fun testQuickFixAll(name: String) {
    val inspection = InspectionTestUtil.instantiateTool(inspection.javaClass) ?: error("No inspection to test.")
    testQuickFix(name, InspectionsBundle.message("fix.all.inspection.problems.in.file", inspection.displayName))
  }

  protected fun testQuickFixUnavailable(name: String, hint: String) {
    myFixture.configureByFile("$name.$fileExt")
    assertEmpty("Quickfix '$hint' is available but should not.", myFixture.filterAvailableIntentions(hint))
  }

  protected fun testQuickFixUnavailableAll(name: String) {
    val inspection = InspectionTestUtil.instantiateTool(inspection.javaClass) ?: error("No inspection to test.")
    testQuickFixUnavailable(name, InspectionsBundle.message("fix.all.inspection.problems.in.file", inspection.displayName))
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