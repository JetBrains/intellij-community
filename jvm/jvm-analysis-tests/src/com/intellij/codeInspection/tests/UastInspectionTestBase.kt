package com.intellij.codeInspection.tests

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.codeInspection.InspectionsBundle
import com.intellij.codeInspection.ex.QuickFixWrapper
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.roots.LanguageLevelProjectExtension
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.InspectionTestUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import java.io.File

abstract class UastInspectionTestBase : LightJavaCodeInsightFixtureTestCase() {
  override fun getTestDataPath(): String = PathManager.getCommunityHomePath().replace(File.separatorChar, '/') + basePath

  abstract val inspection: InspectionProfileEntry

  open val languageLevel = LanguageLevel.JDK_11

  open val sdkLevel = LanguageLevel.JDK_11

  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(inspection)
    LanguageLevelProjectExtension.getInstance(project).languageLevel = languageLevel
  }

  override fun getProjectDescriptor(): LightProjectDescriptor = ProjectDescriptor(sdkLevel)

  protected fun JavaCodeInsightTestFixture.setLanguageLevel(languageLevel: LanguageLevel) {
    LanguageLevelProjectExtension.getInstance(project).languageLevel = languageLevel
    IdeaTestUtil.setModuleLanguageLevel(myFixture.module, languageLevel, testRootDisposable)
  }

  protected fun JavaCodeInsightTestFixture.testHighlighting(
    lang: ULanguage,
    text: String,
    fileName: String = generateFileName()
  ) {
    configureByText("$fileName${lang.ext}", text)
    checkHighlighting()
  }

  /**
   * Checks whether preview matches [preview] specified by the quickfix [hint] at the cursor position marked with <caret> in [code].
   */
  protected fun JavaCodeInsightTestFixture.testPreview(
    lang: ULanguage,
    code: String,
    preview: String,
    hint: String,
    fileName: String = generateFileName()
  ) {
    configureByText("$fileName${lang.ext}", code)
    val action = getAvailableIntention(hint) ?: throw AssertionError("Quickfix '$hint' is not available.")
    val actualPreview = getIntentionPreviewText(action)
    assertEquals(preview, actualPreview)
  }

  /**
   * Runs all quickfixes in [hints] on [before] at the cursor position marked with <caret> and compares it with [after].
   */
  protected fun JavaCodeInsightTestFixture.testQuickFix(
    lang: ULanguage,
    before: String,
    after: String,
    vararg hints: String = arrayOf(InspectionsBundle.message(
      "fix.all.inspection.problems.in.file", InspectionTestUtil.instantiateTool(inspection.javaClass).displayName
    )),
    fileName: String = generateFileName()
  ) {
    configureByText("$fileName${lang.ext}", before)
    hints.forEach { runQuickFix(it) }
    checkResult(after)
  }

  /**
   * Runs all quickfixes in [hints] on [before] and compares it with [after]. This test checks all quick fixes in [before], compared to
   * [testQuickFix] which only runs the quick fix at the <caret> position.
   */
  protected fun JavaCodeInsightTestFixture.testAllQuickfixes(
    lang: ULanguage,
    before: String,
    after: String,
    vararg hints: String = emptyArray(),
    fileName: String = generateFileName()
  ) {
    configureByText("$fileName${lang.ext}", before)
    myFixture.getAllQuickFixes()
      .filterIsInstance(QuickFixWrapper::class.java)
      .filter {(hints.isEmpty() || hints.contains(it.fix.familyName)) }
      .forEach { myFixture.launchAction(it) }
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
    ),
    fileName: String = generateFileName()
  ) {
    configureByText("$fileName${lang.ext}", text)
    assertEmpty("Quickfix '$hint' is available but should not.", myFixture.filterAvailableIntentions(hint))
  }

  protected fun JavaCodeInsightTestFixture.testQuickFixUnavailable(file: String, hint: String = InspectionsBundle.message(
    "fix.all.inspection.problems.in.file", InspectionTestUtil.instantiateTool(inspection.javaClass).displayName
  )) {
    configureByFile(file)
    assertEmpty("Quickfix '$hint' is available but should not.", myFixture.filterAvailableIntentions(hint))
  }

  private fun generateFileName() = getTestName(false).replace("[^a-zA-Z0-9\\.\\-]", "_")

  override fun tearDown() {
    try {
      myFixture.disableInspections(inspection)
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }
}