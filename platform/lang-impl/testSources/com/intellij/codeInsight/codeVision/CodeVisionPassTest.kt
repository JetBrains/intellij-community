// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.codeVision

import com.intellij.codeInsight.codeVision.ui.model.TextCodeVisionEntry
import com.intellij.codeInsight.hints.codeVision.DaemonBoundCodeVisionProvider
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.testFramework.IndexingTestUtil.Companion.suspendUntilIndexesAreReady
import com.intellij.testFramework.LoggedErrorProcessor
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
import com.intellij.testFramework.utils.codeVision.CodeVisionTestUtil
import com.intellij.util.ArrayUtilRt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

@TestApplication
class CodeVisionPassTest {
  private val testDisposable by disposableFixture()
  private val projectFixture = projectFixture()
  private val project by projectFixture
  private val psiFileFixture = projectFixture.moduleFixture().sourceRootFixture().psiFileFixture("test.txt", "content")
  private val psiFile by psiFileFixture
  private val editor by psiFileFixture.editorFixture()

  private val error = NoSuchMethodError("Broken provider")

  @Suppress("unused")
  private val brokenProvider by extensionPointFixture(DaemonBoundCodeVisionProvider.extensionPoint) {
    TestDaemonBoundCodeVisionProvider("broken") {
      throw error
    }
  }

  @Suppress("unused")
  private val workingProvider by extensionPointFixture(DaemonBoundCodeVisionProvider.extensionPoint) {
    TestDaemonBoundCodeVisionProvider("working") {
      listOf(TextRange(0, 0) to TextCodeVisionEntry("lens", it))
    }
  }

  @Test
  fun `failing provider does not stop other providers`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    suspendUntilIndexesAreReady(project)
    val loggedError = LoggedErrorProcessor.executeAndReturnLoggedError {
      CodeInsightTestFixtureImpl.instantiateAndRun(psiFile, editor, ArrayUtilRt.EMPTY_INT_ARRAY, false, false)
    }
    assertSame(error, loggedError.cause)

    project.service<CodeVisionHost>().calculateCodeVisionSync(editor, testDisposable).await()
    assertEquals("content/*<# [lens] #>*/", CodeVisionTestUtil.dumpCodeVisionHints(editor.document.text, editor, true))
  }
}

internal class TestDaemonBoundCodeVisionProvider(
  override val id: String,
  private val compute: (providerId: String) -> List<Pair<TextRange, CodeVisionEntry>>,
) : DaemonBoundCodeVisionProvider {
  override fun computeForEditor(editor: Editor, file: PsiFile): List<Pair<TextRange, CodeVisionEntry>> = compute(id)

  override val name: String
    get() = id
  override val relativeOrderings: List<CodeVisionRelativeOrdering>
    get() = emptyList()
  override val defaultAnchor: CodeVisionAnchorKind
    get() = CodeVisionAnchorKind.Right
}
