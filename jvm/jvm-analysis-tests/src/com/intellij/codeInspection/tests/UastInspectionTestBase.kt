package com.intellij.codeInspection.tests

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.codeInspection.InspectionsBundle
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.roots.LanguageLevelProjectExtension
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

  protected open val languageLevel = LanguageLevel.JDK_1_8

  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(inspection)
    LanguageLevelProjectExtension.getInstance(project).languageLevel = languageLevel
  }

  override fun tuneFixture(moduleBuilder: JavaModuleFixtureBuilder<*>) {
    super.tuneFixture(moduleBuilder)
    moduleBuilder.addJdk(IdeaTestUtil.getMockJdk18Path().path)
  }

  enum class ULanguage(val ext: String) { JAVA(".java"), KOTLIN(".kt") }

  protected fun JavaCodeInsightTestFixture.testHighlighting(lang: ULanguage, text: String) {
    configureByText("UnderTest${lang.ext}", text)
    checkHighlighting()
  }


  protected fun JavaCodeInsightTestFixture.testQuickFix(
    lang: ULanguage,
    before: String,
    after: String,
    hint: String = InspectionsBundle.message(
      "fix.all.inspection.problems.in.file", InspectionTestUtil.instantiateTool(inspection.javaClass).displayName
    )
  ) {
    configureByText("UnderTest${lang.ext}", before)
    runQuickFix(hint)
    checkResult(after)
  }

  protected fun JavaCodeInsightTestFixture.testQuickFix(file: String, hint: String = InspectionsBundle.message(
    "fix.all.inspection.problems.in.file", InspectionTestUtil.instantiateTool(inspection.javaClass).displayName
  )) {
    configureByFile(file)
    runQuickFix(hint)
    checkResultByFile(file.replace(".", ".after."))
  }

  private fun JavaCodeInsightTestFixture.runQuickFix(hint: String) {
    val action = getAvailableIntention(hint) ?: throw AssertionError("Quickfix '$hint' is not available.")
    launchAction(action)
  }

  protected fun JavaCodeInsightTestFixture.testQuickFixUnavailable(
    lang: ULanguage,
    text: String,
    hint: String = InspectionsBundle.message(
      "fix.all.inspection.problems.in.file", InspectionTestUtil.instantiateTool(inspection.javaClass).displayName
    )
  ) {
    configureByText("UnderTest${lang.ext}", text)
    assertEmpty("Quickfix '$hint' is available but should not.", myFixture.filterAvailableIntentions(hint))
  }

  protected fun JavaCodeInsightTestFixture.testQuickFixUnavailable(file: String, hint: String = InspectionsBundle.message(
    "fix.all.inspection.problems.in.file", InspectionTestUtil.instantiateTool(inspection.javaClass).displayName
  )) {
    configureByFile(file)
    assertEmpty("Quickfix '$hint' is available but should not.", myFixture.filterAvailableIntentions(hint))
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