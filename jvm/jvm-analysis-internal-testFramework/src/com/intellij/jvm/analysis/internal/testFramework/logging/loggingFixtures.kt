// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("LoggingFixtures")

package com.intellij.jvm.analysis.internal.testFramework.logging

import com.intellij.analysis.logging.resolve.LoggingArgumentSymbol
import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.codeInspection.logging.LoggingConditionDisagreesWithLogLevelStatementInspection
import com.intellij.codeInspection.logging.LoggingGuardedByConditionInspection
import com.intellij.codeInspection.logging.LoggingPlaceholderCountMatchesArgumentCountInspection
import com.intellij.codeInspection.logging.LoggingSimilarMessageInspection
import com.intellij.codeInspection.logging.LoggingStatementNotGuardedByLogConditionInspection
import com.intellij.codeInspection.logging.LoggingUtil
import com.intellij.jvm.analysis.internal.testFramework.logging.LoggingTestUtils.addAkka
import com.intellij.jvm.analysis.internal.testFramework.logging.LoggingTestUtils.addJUL
import com.intellij.jvm.analysis.internal.testFramework.logging.LoggingTestUtils.addKotlinAdapter
import com.intellij.jvm.analysis.internal.testFramework.logging.LoggingTestUtils.addLog4J
import com.intellij.jvm.analysis.internal.testFramework.logging.LoggingTestUtils.addSlf4J
import com.intellij.jvm.analysis.testFramework.JvmCodeInsightFixture
import com.intellij.jvm.analysis.testFramework.JvmInspectionFixture
import com.intellij.jvm.analysis.testFramework.jvmCodeInsightFixture
import com.intellij.jvm.analysis.testFramework.jvmInspectionFixture
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.model.psi.PsiSymbolReference
import com.intellij.model.psi.PsiSymbolReferenceService
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.util.TextRange
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import org.jetbrains.annotations.TestOnly
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UPolyadicExpression
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * Creates the fixture of a logging inspection test. It replaces [LoggingInspectionTestBase].
 * The fixture adds the SLF4J, Log4j, JUL, Kotlin adapter and Akka stubs of [LoggingTestUtils].
 * Give it the project of [com.intellij.jvm.analysis.testFramework.jvmProjectFixture].
 */
@TestOnly
@JvmOverloads
fun <T : InspectionProfileEntry> loggingInspectionFixture(
  projectFixture: TestFixture<LightJavaProject>,
  testDataPath: String? = null,
  inspection: () -> T,
): TestFixture<JvmInspectionFixture<T>> = testFixture("loggingInspectionFixture") {
  val logging = jvmInspectionFixture(projectFixture, testDataPath, inspection = inspection).init()
  addSlf4J(logging)
  addLog4J(logging)
  addJUL(logging)
  addKotlinAdapter(logging)
  addAkka(logging)
  initialized(logging) {}
}

/**
 * Creates the fixture of a [LoggingConditionDisagreesWithLogLevelStatementInspection] test.
 * It replaces [LoggingConditionDisagreesWithLogLevelStatementInspectionTestBase].
 */
@TestOnly
fun loggingConditionDisagreesWithLogLevelStatementInspectionFixture(
  projectFixture: TestFixture<LightJavaProject>,
): TestFixture<JvmInspectionFixture<LoggingConditionDisagreesWithLogLevelStatementInspection>> =
  loggingInspectionFixture(projectFixture) { LoggingConditionDisagreesWithLogLevelStatementInspection() }

/**
 * Creates the fixture of a [LoggingGuardedByConditionInspection] test. It replaces [LoggingGuardedByConditionInspectionTestBase].
 * The fixture sets the level of the inspection to a warning.
 * It sets [LoggingGuardedByConditionInspection.showOnlyIfFixPossible] when the name of the test ends with `showOnlyIfFixPossible`.
 */
@TestOnly
fun loggingGuardedByConditionInspectionFixture(
  projectFixture: TestFixture<LightJavaProject>,
): TestFixture<JvmInspectionFixture<LoggingGuardedByConditionInspection>> = testFixture("loggingGuardedByConditionInspectionFixture") {
  val logging = loggingInspectionFixture(projectFixture) { LoggingGuardedByConditionInspection() }.init()
  setWarningLevel(logging)
  logging.inspection.showOnlyIfFixPossible = logging.getTestName(true).endsWith("showOnlyIfFixPossible")
  initialized(logging) {}
}

/**
 * Creates the fixture of a [LoggingPlaceholderCountMatchesArgumentCountInspection] test.
 * It replaces [LoggingPlaceholderCountMatchesArgumentCountInspectionTestBase].
 * The inspection has [LoggingPlaceholderCountMatchesArgumentCountInspection.Slf4jToLog4J2Type.YES].
 */
@TestOnly
fun loggingPlaceholderCountMatchesArgumentCountInspectionFixture(
  projectFixture: TestFixture<LightJavaProject>,
): TestFixture<JvmInspectionFixture<LoggingPlaceholderCountMatchesArgumentCountInspection>> =
  loggingInspectionFixture(projectFixture) {
    LoggingPlaceholderCountMatchesArgumentCountInspection().apply {
      slf4jToLog4J2Type = LoggingPlaceholderCountMatchesArgumentCountInspection.Slf4jToLog4J2Type.YES
    }
  }

/**
 * Creates the fixture of a [LoggingSimilarMessageInspection] test. It replaces [LoggingSimilarMessageInspectionTestBase].
 */
@TestOnly
fun loggingSimilarMessageInspectionFixture(
  projectFixture: TestFixture<LightJavaProject>,
): TestFixture<JvmInspectionFixture<LoggingSimilarMessageInspection>> =
  loggingInspectionFixture(projectFixture) { LoggingSimilarMessageInspection() }

/**
 * Creates the fixture of a [LoggingStatementNotGuardedByLogConditionInspection] test.
 * It replaces [LoggingStatementNotGuardedByLogConditionInspectionTestBase].
 * The inspection has [LoggingUtil.LimitLevelType.INFO_AND_LOWER], and its level is a warning.
 * The fixture sets [LoggingStatementNotGuardedByLogConditionInspection.flagUnguardedConstant] when the name of the test ends with `flagUnguardedConstant`.
 */
@TestOnly
fun loggingStatementNotGuardedByLogConditionInspectionFixture(
  projectFixture: TestFixture<LightJavaProject>,
): TestFixture<JvmInspectionFixture<LoggingStatementNotGuardedByLogConditionInspection>> =
  testFixture("loggingStatementNotGuardedByLogConditionInspectionFixture") {
    val logging = loggingInspectionFixture(projectFixture) { LoggingStatementNotGuardedByLogConditionInspection() }.init()
    logging.inspection.myLimitLevelType = LoggingUtil.LimitLevelType.INFO_AND_LOWER
    setWarningLevel(logging)
    logging.inspection.flagUnguardedConstant = logging.getTestName(true).endsWith("flagUnguardedConstant")
    initialized(logging) {}
  }

private fun setWarningLevel(fixture: JvmInspectionFixture<*>) {
  val displayKey = HighlightDisplayKey.find(fixture.inspection.shortName)
  val currentProfile = ProjectInspectionProfileManager.getInstance(fixture.project).currentProfile
  currentProfile.setErrorLevel(displayKey!!, HighlightDisplayLevel.WARNING, fixture.project)
}

/**
 * The fixture of a test of the logging placeholder highlighting. It is the JUnit 5 form of [LoggingPlaceholderAnnotatorTestBase].
 * Create it with [loggingPlaceholderAnnotatorFixture].
 */
@TestOnly
class LoggingPlaceholderAnnotatorFixture internal constructor(
  base: JvmCodeInsightFixture,
  private val fileName: String,
) : JvmCodeInsightFixture(base.fixture) {
  /**
   * Configures [expectedText] without the `placeholder` tags in the file of the test,
   * and compares the highlighted placeholders with the tags.
   */
  fun doTest(expectedText: String) {
    val expectedTextWithoutTags = expectedText.replace(Regex("</?$TAG>"), "")
    configureByText(fileName, expectedTextWithoutTags)
    val highlightingList = doHighlighting(HighlightSeverity.INFORMATION).filter {
      it.forcedTextAttributesKey?.externalName == "LOG_STRING_PLACEHOLDER"
    }
    val restoredText = runReadActionBlocking { CodeInsightTestFixtureImpl.getTagsFromSegments(file.text, highlightingList, TAG, null) }
    assertEquals(expectedText, restoredText)
  }

  private companion object {
    private const val TAG = "placeholder"
  }
}

/**
 * Creates a [LoggingPlaceholderAnnotatorFixture] for one test. [fileName] is the name of the file of each test.
 * The fixture adds the SLF4J, Log4j, JUL and Kotlin adapter stubs of [LoggingTestUtils].
 * Give it the project of [com.intellij.jvm.analysis.testFramework.jvmProjectFixture].
 */
@TestOnly
fun loggingPlaceholderAnnotatorFixture(
  projectFixture: TestFixture<LightJavaProject>,
  fileName: String,
): TestFixture<LoggingPlaceholderAnnotatorFixture> = testFixture("loggingPlaceholderAnnotatorFixture") {
  val logging = LoggingPlaceholderAnnotatorFixture(jvmCodeInsightFixture(projectFixture).init(), fileName)
  addSlf4J(logging)
  addLog4J(logging)
  addJUL(logging)
  addKotlinAdapter(logging)
  initialized(logging) {}
}

/**
 * The fixture of a test of the logging argument references. It is the JUnit 5 form of [LoggingArgumentSymbolReferenceProviderTestBase].
 * Create it with [loggingArgumentSymbolReferenceProviderFixture].
 */
@TestOnly
class LoggingArgumentSymbolReferenceProviderFixture internal constructor(
  base: JvmCodeInsightFixture,
) : JvmCodeInsightFixture(base.fixture) {
  /**
   * Checks that the references of the logging string at the caret resolve to the arguments in [bindings]. It reads the PSI in a read action.
   */
  fun doTest(bindings: Map<TextRange, String>) {
    runReadActionBlocking {
      val literalExpression = PsiTreeUtil.getParentOfType(file.findElementAt(editor.caretModel.offset), PsiLanguageInjectionHost::class.java)
      assertFalse(literalExpression == null)
      val refs: Collection<PsiSymbolReference> = PsiSymbolReferenceService.getService().getReferences(literalExpression!!)
      assertEquals(bindings.size, refs.size)
      val usedRanges = mutableSetOf<TextRange>()
      refs.forEach { ref ->
        assertEquals(literalExpression, ref.element)
        val symbols = ref.resolveReference()
        assertEquals(1, symbols.size)
        val symbol = symbols.single()
        assertTrue(symbol is LoggingArgumentSymbol)
        val formatSymbol = symbol as LoggingArgumentSymbol
        val logString = formatSymbol.getPlaceholderString()
        assertTrue(logString is ULiteralExpression || logString is UPolyadicExpression)
        val expressionText = formatSymbol.expression.text
        assertFalse(ref.rangeInElement in usedRanges)
        assertEquals(bindings[ref.rangeInElement], expressionText)
        usedRanges.add(ref.rangeInElement)
      }
      assertTrue(bindings.map { it.key }.toSet() == usedRanges)
    }
  }
}

/**
 * Creates the project of a [LoggingArgumentSymbolReferenceProviderFixture] test class with the latest JDK.
 */
@TestOnly
fun loggingArgumentSymbolReferenceProviderProjectFixture(): TestFixture<LightJavaProject> =
  lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_LATEST_WITH_LATEST_JDK)

/**
 * Creates a [LoggingArgumentSymbolReferenceProviderFixture] for one test.
 * The fixture adds the SLF4J, Log4j, JUL and Kotlin adapter stubs of [LoggingTestUtils].
 * Give it the project of [loggingArgumentSymbolReferenceProviderProjectFixture].
 */
@TestOnly
fun loggingArgumentSymbolReferenceProviderFixture(
  projectFixture: TestFixture<LightJavaProject>,
): TestFixture<LoggingArgumentSymbolReferenceProviderFixture> = testFixture("loggingArgumentSymbolReferenceProviderFixture") {
  val logging = LoggingArgumentSymbolReferenceProviderFixture(jvmCodeInsightFixture(projectFixture).init())
  addSlf4J(logging)
  addLog4J(logging)
  addJUL(logging)
  addKotlinAdapter(logging)
  initialized(logging) {}
}
