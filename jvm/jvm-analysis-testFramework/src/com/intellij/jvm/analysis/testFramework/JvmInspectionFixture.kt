// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("JvmInspectionFixtures")

package com.intellij.jvm.analysis.testFramework

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.codeInspection.InspectionWrapperUtil
import com.intellij.codeInspection.InspectionsBundle
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection
import com.intellij.codeInspection.ex.QuickFixWrapper
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager
import com.intellij.testFramework.InspectionTestUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import java.util.function.Function

/**
 * The code insight fixture of a JVM inspection test. It is the JUnit 5 form of [JvmInspectionTestBase].
 * Create it with [jvmInspectionFixture].
 *
 * The fixture enables [inspection] before the test and disables it after the test.
 * The highlighting helpers run on the calling thread. The quick fix helpers run on the EDT from the configuration to the last check.
 */
@TestOnly
open class JvmInspectionFixture<T : InspectionProfileEntry>(
  fixture: LightJavaCodeInsightTestFixture,
  /**
   * The inspection of the test. The fixture creates it for each test.
   */
  val inspection: T,
) : JvmCodeInsightFixture(fixture) {
  /**
   * Sets the level of [inspection] in the project profile to [HighlightDisplayLevel.WARNING].
   */
  fun enableWarnings() {
    val profile = ProjectInspectionProfileManager.getInstance(project).currentProfile
    val toolWrapper = InspectionWrapperUtil.wrapTool(inspection)
    profile.setErrorLevel(toolWrapper.displayKey!!, HighlightDisplayLevel.WARNING, project)
  }

  /**
   * Returns the name of the "fix all problems in file" quick fix of [inspection]. It is the default hint of the quick fix helpers.
   */
  fun fixAllHint(): String {
    return InspectionsBundle.message("fix.all.inspection.problems.in.file", InspectionTestUtil.instantiateTool(inspection.javaClass).displayName)
  }

  /**
   * Configures [text] in a file of [lang] and checks the highlighting.
   */
  fun testHighlighting(lang: JvmLanguage, text: String, fileName: String = generateFileName()) {
    configureByText("$fileName${lang.ext}", text)
    checkHighlighting()
  }

  /**
   * Runs the [hint] quick fix on [before] at the caret and compares the result with [after].
   * With [testPreview], it first compares the preview of the quick fix with [after].
   */
  fun testQuickFix(
    lang: JvmLanguage,
    before: String,
    after: String,
    hint: String = fixAllHint(),
    testPreview: Boolean = false,
    fileName: String = generateFileName(),
  ) {
    runInEdtAndWait {
      configureByText("$fileName${lang.ext}", before)
      if (testPreview) {
        configureByText("$fileName${lang.ext}", before)
        assertEquals(after, getIntentionPreviewText(getIntention(hint)))
      }
      runQuickFix(hint)
      checkResult(after)
    }
  }

  /**
   * Runs all quick fixes in [hints] on [before] and calls [test] with the exception that they throw.
   */
  inline fun <reified E : Throwable> testQuickFixException(
    lang: JvmLanguage,
    before: String,
    vararg hints: String = arrayOf(fixAllHint()),
    fileName: String = generateFileName(),
    noinline test: (E) -> Unit,
  ) {
    runInEdtAndWait {
      configureByText("$fileName${lang.ext}", before)
      val error = runCatching { hints.forEach { runQuickFix(it) } }.exceptionOrNull()
      if (error == null) fail<Unit>("Expected exception ${E::class} to be but nothing was thrown")
      if (error !is E) fail<Unit>("Expected exception ${E::class} but was ${error!!::class}")
      test(error as E)
    }
  }

  /**
   * Runs all quick fixes of the file whose family name is in [hints] on [before] and compares the result with [after].
   * When [hints] is empty, it runs all quick fixes. Compare [testQuickFix], which runs only the quick fix at the caret.
   */
  fun testAllQuickfixes(
    lang: JvmLanguage,
    before: String,
    after: String,
    vararg hints: String = emptyArray(),
    fileName: String = generateFileName(),
  ) {
    runInEdtAndWait {
      configureByText("$fileName${lang.ext}", before)
      getAllQuickFixes()
        .filter { action ->
          val fix = QuickFixWrapper.unwrap(action)
          fix != null && (hints.isEmpty() || hints.contains(fix.familyName))
        }
        .forEach { launchAction(it) }
      checkResult(after)
    }
  }

  /**
   * Runs the [hint] quick fix on the test data file [file] and compares the result with the file whose name has `.after.` in place of each dot.
   */
  fun testQuickFix(file: String, hint: String = fixAllHint(), checkPreview: Boolean = false) {
    runInEdtAndWait {
      configureByFile(file)
      runQuickFix(hint, checkPreview)
      checkResultByFile(file.replace(".", ".after."))
    }
  }

  /**
   * Runs the [hint] quick fix at the caret. With [checkPreview], it first checks the preview of the quick fix. It runs on the EDT.
   */
  fun runQuickFix(hint: String, checkPreview: Boolean = false) {
    runInEdtAndWait {
      val action = getIntention(hint)
      if (checkPreview) {
        checkPreviewAndLaunchAction(action)
      }
      else {
        launchAction(action)
      }
    }
  }

  private fun getIntention(hint: String): IntentionAction {
    return getAvailableIntention(hint) ?: throw AssertionError(
      "Quick-fix '$hint' not in list of available intentions:\n${availableIntentions.joinToString(separator = "\n") { it.text }}"
    )
  }

  /**
   * Configures [text] in a file of [lang] and checks that the [hint] quick fix is not available.
   */
  fun testQuickFixUnavailable(lang: JvmLanguage, text: String, hint: String = fixAllHint(), fileName: String = generateFileName()) {
    configureByText("$fileName${lang.ext}", text)
    assertQuickFixUnavailable(hint)
  }

  /**
   * Configures the test data file [file] and checks that the [hint] quick fix is not available.
   */
  fun testQuickFixUnavailable(file: String, hint: String = fixAllHint()) {
    configureByFile(file)
    assertQuickFixUnavailable(hint)
  }

  private fun assertQuickFixUnavailable(hint: String) {
    val available = runInEdtAndGet { filterAvailableIntentions(hint) }
    assertTrue(available.isEmpty(), "Quickfix '$hint' is available but should not: $available")
  }
}

/**
 * Creates a [JvmInspectionFixture] for one test in the project of [projectFixture]. Declare it as an instance field.
 * Give it the project of [jvmProjectFixture], or of [com.intellij.testFramework.fixtures.lightJavaProjectFixture] with the descriptor of the class.
 *
 * [inspection] creates the inspection of the test. [testDataPath] is the absolute test data path.
 * When it is null, the test data path is the community directory, as in [JvmInspectionTestBase].
 * A class that picks the descriptor for each test gives [descriptor], as [lightJavaCodeInsightFixture] describes.
 */
@TestOnly
@JvmOverloads
fun <T : InspectionProfileEntry> jvmInspectionFixture(
  projectFixture: TestFixture<LightJavaProject>,
  testDataPath: String? = null,
  descriptor: Function<String, LightProjectDescriptor>? = null,
  inspection: () -> T,
): TestFixture<JvmInspectionFixture<T>> = testFixture("jvmInspectionFixture") {
  val fixture = lightJavaCodeInsightFixture(projectFixture, descriptor).init()
  fixture.testDataPath = testDataPath ?: communityTestDataPath()
  val tool = inspection()
  fixture.enableInspections(tool)
  initialized(JvmInspectionFixture(fixture, tool)) {
    fixture.disableInspections(tool)
  }
}

/**
 * Creates the fixture of an implicit usage provider test. It replaces [JvmImplicitUsageProviderTestBase].
 * The fixture enables [UnusedDeclarationInspection] with the local check.
 */
@TestOnly
@JvmOverloads
fun jvmImplicitUsageProviderFixture(
  projectFixture: TestFixture<LightJavaProject>,
  testDataPath: String? = null,
): TestFixture<JvmInspectionFixture<UnusedDeclarationInspection>> =
  jvmInspectionFixture(projectFixture, testDataPath) { UnusedDeclarationInspection(true) }
