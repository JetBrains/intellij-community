// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.quickFix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.impl.preview.IntentionPreviewPopupUpdateProcessor
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.Comparing
import com.intellij.psi.util.PsiUtil
import com.intellij.psi.util.PsiUtilBase
import com.intellij.testFramework.FileBasedArgumentProvider
import com.intellij.testFramework.FileBasedTestCaseHelperEx
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.CodeInsightTestUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase5
import com.intellij.testFramework.UsefulTestCase.assertSameLinesWithFile
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.fail
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ArgumentsSource

/**
 * @deprecated Use [LightQuickFixFileBasedTest] with [com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture] in a JUnit 5 test.
 */
@Deprecated("Use LightQuickFixFileBasedTest with lightJavaCodeInsightFixture")
abstract class LightQuickFixParameterizedTestCase5(projectDescriptor: LightProjectDescriptor? = null)
  : LightJavaCodeInsightFixtureTestCase5(projectDescriptor), FileBasedTestCaseHelperEx {
  abstract override fun getTestDataPath(): String

  override fun getRelativeBasePath(): String {
    return ""
  }

  override fun getFileSuffix(fileName: String): String? {
    return if (!fileName.startsWith(LightQuickFixTestCase.BEFORE_PREFIX)) null
    else fileName.substring(LightQuickFixTestCase.BEFORE_PREFIX.length)
  }

  override fun getBaseName(fileAfterSuffix: String): String? {
    return if (!fileAfterSuffix.startsWith(LightQuickFixTestCase.AFTER_PREFIX)) null
    else fileAfterSuffix.substring(LightQuickFixTestCase.AFTER_PREFIX.length)
  }

  @ParameterizedTest(name = ParameterizedTest.ARGUMENTS_WITH_NAMES_PLACEHOLDER)
  @ArgumentsSource(FileBasedArgumentProvider::class)
  @Throws(Throwable::class)
  fun parameterized(fileName: String) {
    doLightQuickFixFileTest(fixture, getTestDataPath(), fileName)
  }

}

/**
 * Checks one test of a [LightQuickFixFileBasedTest]: the `before` file [fileName] of [testDataPath] with the action hint in it.
 * The function finds the action of the hint, launches it, and compares the result with the `after` file.
 * For a `-preview` hint, it also compares the intention preview with the `preview` file, or with the result when no `preview` file exists.
 * The action must have a family name that is not blank.
 * When [exactMatch] is false, the hint text is a prefix of the action text.
 * It runs off the EDT.
 */
@TestOnly
@JvmOverloads
fun doLightQuickFixFileTest(fixture: JavaCodeInsightTestFixture, testDataPath: String, fileName: String, exactMatch: Boolean = true) {
  val filePath = LightQuickFixTestCase.BEFORE_PREFIX + fileName
  val file = fixture.configureByFile(filePath)
  val (hint, context) = runReadAction { ActionHint.parse(file, file.text, exactMatch) to fixture.actionContext }
  val action = hint.findAndCheck(fixture.availableIntentions, context) {
    """
           Test: $testDataPath/$filePath
           Language level: ${PsiUtil.getLanguageLevel(fixture.project)}
           SDK: ${ModuleRootManager.getInstance(ModuleUtilCore.findModuleForFile(fixture.file)!!).sdk}
           Infos: ${LightQuickFixTestCase.getCurrentHighlightingInfo(fixture.doHighlighting())}
           """.trimIndent()
  }
  if (action != null) {
    val text = action.text
    val element = runReadAction { PsiUtilBase.getElementAtCaret(fixture.editor) }
    if (hint.shouldCheckPreview()) {
      checkPreviewAndLaunchAction(fixture, action, testDataPath, LightQuickFixTestCase.PREVIEW_PREFIX + fileName)
    }
    else {
      fixture.launchAction(action)
    }

    runInEdtAndWait { UIUtil.dispatchAllInvocationEvents() }

    val intentions = fixture.availableIntentions
    val afterAction = CodeInsightTestUtil.findIntentionByText(intentions, text)
    if (afterAction != null && Comparing.equal(element, runReadAction { PsiUtilBase.getElementAtCaret(fixture.editor) })) {
      fail("Action '$text' is still available after its invocation in test $filePath")
    }
    runInEdtAndWait { NonBlockingReadActionImpl.waitForAsyncTaskCompletion() }
    fixture.checkResultByFile(LightQuickFixTestCase.AFTER_PREFIX + fileName)
    if (action.familyName.isBlank()) {
      fail("Action '$text' provides empty family name which means that user would see action with empty presentable text in Inspection Results")
    }
  }
}

private fun checkPreviewAndLaunchAction(fixture: JavaCodeInsightTestFixture, action: IntentionAction, testDataPath: String, previewFilePath: String) {
  // Run in a background thread to catch accidental write actions during the preview generation
  val previewContent = ReadAction.nonBlocking<String> {
    IntentionPreviewPopupUpdateProcessor.getPreviewContent(fixture.project, action, fixture.file, fixture.editor)
  }.submit(AppExecutorUtil.getAppExecutorService()).get()
  fixture.launchAction(action)
  runInEdtAndWait { NonBlockingReadActionImpl.waitForAsyncTaskCompletion() }
  val path = Path.of(testDataPath, previewFilePath)
  if (Files.exists(path)) {
    assertSameLinesWithFile(path.toString(), previewContent)
  }
  else {
    if (previewContent.isEmpty()) {
      fail("No preview was generated for '${action.text}'")
    }
    assertEquals(runReadActionBlocking { fixture.file.text }, previewContent)
  }
}
