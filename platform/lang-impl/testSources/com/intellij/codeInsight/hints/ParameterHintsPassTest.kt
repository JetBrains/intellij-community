// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints

import com.intellij.codeInsight.daemon.impl.ParameterHintsPresentationManager
import com.intellij.lang.Language
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.testFramework.IndexingTestUtil.Companion.suspendUntilIndexesAreReady
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.editorFixture
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.psiFileFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.util.ArrayUtilRt
import kotlinx.coroutines.Dispatchers
import org.jetbrains.concurrency.CancellablePromise
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
class ParameterHintsPassTest {
  private val projectFixture = projectFixture()
  private val psiFileFixture = projectFixture.moduleFixture().sourceRootFixture().psiFileFixture("test.txt", "content")
  private val psiFile by psiFileFixture
  private val editor by psiFileFixture.editorFixture()

  private val error = NoSuchMethodError("Broken provider")

  @Suppress("unused")
  private val provider by parameterHintsProviderFixture(PlainTextLanguage.INSTANCE) {
    object : InlayParameterHintsProvider {
      override fun getParameterHints(element: PsiElement, file: PsiFile): List<InlayInfo> =
        if (element === file) listOf(InlayInfo("hint", 0)) else throw error

      override fun getDefaultBlackList(): Set<String> = emptySet()

      override fun canShowHintsWhenDisabled(): Boolean = true
    }
  }

  @Test
  fun `hints collected before provider failure are inserted`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    suspendUntilIndexesAreReady(projectFixture.get())
    val loggedError = LoggedErrorProcessor.executeAndReturnLoggedError {
      CodeInsightTestFixtureImpl.instantiateAndRun(psiFile, editor, ArrayUtilRt.EMPTY_INT_ARRAY, false, false)
    }
    assertSame(error, loggedError.cause)
    val manager = ParameterHintsPresentationManager.getInstance()
    val hints = manager.getParameterHintsInRange(editor, 0, editor.document.textLength)
    assertEquals(listOf(0 to "hint:"), hints.map { it.offset to manager.getHintText(it) })
  }

  @Test
  fun `async update inserts hints collected before provider failure`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    suspendUntilIndexesAreReady(projectFixture.get())
    lateinit var promise: CancellablePromise<*>
    val loggedError = LoggedErrorProcessor.executeAndReturnLoggedError {
      promise = ParameterHintsPass.asyncUpdate(psiFile, editor)
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
    }
    assertSame(error, loggedError.cause)
    assertTrue(promise.isSucceeded)
    val manager = ParameterHintsPresentationManager.getInstance()
    val hints = manager.getParameterHintsInRange(editor, 0, editor.document.textLength)
    assertEquals(listOf(0 to "hint:"), hints.map { it.offset to manager.getHintText(it) })
  }
}

@Suppress("SameParameterValue")
private fun parameterHintsProviderFixture(
  language: Language,
  createProvider: () -> InlayParameterHintsProvider,
): TestFixture<InlayParameterHintsProvider> = testFixture {
  val provider = createProvider()
  InlayParameterHintsExtension.addExplicitExtension(language, provider)
  initialized(provider) {
    InlayParameterHintsExtension.removeExplicitExtension(language, provider)
  }
}
