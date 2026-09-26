// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.codeVision

import com.intellij.codeInsight.codeVision.ui.model.TextCodeVisionEntry
import com.intellij.codeInsight.hints.InlayHintsUtils
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.util.awaitWithCheckCanceled
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.disposableFixture
import com.intellij.testFramework.junit5.fixture.editorFixture
import com.intellij.testFramework.junit5.fixture.extensionPointFixture
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.psiFileFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import com.intellij.testFramework.utils.codeVision.CodeVisionTestCase
import com.intellij.util.ArrayUtilRt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture

@TestApplication
class CodeVisionHostTest {
  val testDisposable by disposableFixture()
  val projectFixture = projectFixture()
  val project by projectFixture
  val psiFileFixture = projectFixture.moduleFixture().sourceRootFixture().psiFileFixture("foo", "")
  val psiFile by psiFileFixture
  val editor by psiFileFixture.editorFixture()

  @Nested
  inner class ProviderCancellationTest {
    val computeStateCanProceed = CompletableFuture<Unit>()
    val computeStateReachedRA = CompletableFuture<Unit>()
    @Suppress("unused")
    val testProvider by extensionPointFixture(CodeVisionProvider.providersExtensionPoint) {
      object : TestCodeVisionProvider() {
        override fun computeCodeVision(editor: Editor, uiData: Unit): CodeVisionState {
          return InlayHintsUtils.computeCodeVisionUnderReadAction(expectsIndicator = true) {
            computeStateReachedRA.complete(Unit)
            computeStateCanProceed.awaitWithCheckCanceled()
            CodeVisionState.Ready(listOf(TextRange(0, 0) to TextCodeVisionEntry("foo", id)))
          }
        }
      }
    }

    @Test
    fun `test computeCodeVisionUnderReadAction does not get code vision stuck when write action interferes`() = timeoutRunBlocking(context = Dispatchers.EDT) {
      val codeVisionHost = project.service<CodeVisionHost>()
      // need to run highlighting passes so that DaemonBasedCodeVisionProviders results are ready when calculateCodeVisionSync runs
      runHighlighting(editor, psiFile)
      val done = codeVisionHost.calculateCodeVisionSync(editor, testDisposable)
      computeStateReachedRA.await()
      // cancels currently running RA in TestCodeVisionProvider#computeCodeVision
      runWriteAction { }
      computeStateCanProceed.complete(Unit)
      // the cancellation due to WA should not cancel the whole computation
      done.await()
      assertEquals("/*<# [foo] #>*/", dumpCodeVisionHints(editor))
    }
  }
}


private fun dumpCodeVisionHints(editor: Editor): String {
  return CodeVisionTestCase.dumpCodeVisionHints(editor.document.text, editor, onlyCodeVisionHintsAllowed = true)
}

abstract class TestCodeVisionProvider : CodeVisionProvider<Unit> {
  override fun precomputeOnUiThread(editor: Editor) {}

  override val name: String
    get() = "foo"
  override val relativeOrderings: List<CodeVisionRelativeOrdering>
    get() = emptyList()
  override val defaultAnchor: CodeVisionAnchorKind
    get() = CodeVisionAnchorKind.Right
  override val id: String
    get() = "foo.test.code.vision.provider"
}

private fun runHighlighting(editor: Editor, psiFile: PsiFile) {
  CodeInsightTestFixtureImpl.instantiateAndRun(psiFile, editor, ArrayUtilRt.EMPTY_INT_ARRAY, false, false)
}