// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.copyReference

import com.intellij.model.Pointer
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.application.readAction
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.psi.PsiFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.ui.ChooserInterceptor
import com.intellij.ui.UiInterceptors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertInstanceOf
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable

@TestApplication
internal class CopyReferenceTargetProviderTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val fixture by codeInsightModuleFixture(projectFixture)

  @TestDisposable
  lateinit var disposable: Disposable

  @Test
  fun `copies the target of the provider at the caret`(): Unit = timeoutRunBlocking {
    registerProvider { file, offset -> listOf(TestCopyReferenceTarget(wordAt(file, offset))) }

    val contents = assertInstanceOf<CopyReferenceTransferable>(copyReference("foo b<caret>ar baz"))

    assertEquals("bar", contents.getTransferData(DataFlavor.stringFlavor))
    assertEquals(TestCopyReferenceTarget("bar"), readAction { contents.target })
  }

  @Test
  fun `removes duplicate targets of multiple carets`(): Unit = timeoutRunBlocking {
    registerProvider { file, offset -> listOf(TestCopyReferenceTarget(wordAt(file, offset))) }

    val contents = assertInstanceOf<CopyReferenceTransferable>(copyReference("foo b<caret>a<caret>r baz"))

    assertEquals("bar", contents.getTransferData(DataFlavor.stringFlavor))
  }

  @Test
  fun `copies the chosen target when multiple carets have different targets`(): Unit = timeoutRunBlocking {
    registerProvider { file, offset -> listOf(TestCopyReferenceTarget(wordAt(file, offset))) }

    UiInterceptors.registerPossible(disposable, ChooserInterceptor(null, ".*reference='baz'.*"))

    val contents = assertInstanceOf<CopyReferenceTransferable>(copyReference("foo b<caret>ar b<caret>az"))

    assertEquals("baz", contents.getTransferData(DataFlavor.stringFlavor))
  }

  @Test
  fun `falls back to the file and line when the provider returns no targets`(): Unit = timeoutRunBlocking {
    registerProvider { _, _ -> emptyList() }

    val contents = copyReference("foo\nb<caret>ar baz")

    assertFalse(contents is CopyReferenceTransferable)
    assertEquals("CopyReferenceTargetProviderTest.txt:2", contents.getTransferData(DataFlavor.stringFlavor))
  }

  private fun registerProvider(provider: CopyReferenceTargetProvider) {
    COPY_REFERENCE_TARGET_PROVIDER_EP.point.registerExtension(provider, disposable)
  }

  private suspend fun copyReference(text: String): Transferable = withContext(Dispatchers.EDT) {
    fixture.configureByText("CopyReferenceTargetProviderTest.txt", text)

    CopyPasteManager.getInstance().setContents(StringSelection(""))
    fixture.performEditorAction(IdeActions.ACTION_COPY_REFERENCE)
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion()

    CopyPasteManager.getInstance().contents!!
  }

  private fun wordAt(file: PsiFile, offset: Int): String = file.text.substring(
    file.text.lastIndexOf(' ', offset - 1) + 1, file.text.indexOf(' ', offset).takeIf { it >= 0 } ?: file.text.length
  )

  private data class TestCopyReferenceTarget(private val text: String) : CopyReferenceTarget {
    override fun createPointer(): Pointer<TestCopyReferenceTarget> = Pointer.hardPointer(this)

    override fun getReference(): String = text

    override fun presentation(): TargetPresentation = TargetPresentation.builder(text).presentation()
  }
}
