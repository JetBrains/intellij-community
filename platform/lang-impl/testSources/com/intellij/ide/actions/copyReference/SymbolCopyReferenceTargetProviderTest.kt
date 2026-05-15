// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.copyReference

import com.intellij.model.Pointer
import com.intellij.model.Symbol
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.CustomizedDataContext
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.ui.ChooserInterceptor
import com.intellij.ui.UiInterceptors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertInstanceOf
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable

@TestApplication
internal class SymbolCopyReferenceTargetProviderTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val fixture by codeInsightModuleFixture(projectFixture)

  @TestDisposable
  lateinit var disposable: Disposable

  @Test
  fun `copies the target of the provider for the symbol`(): Unit = timeoutRunBlocking {
    registerProvider { _, symbol -> listOf(TestCopyReferenceTarget((symbol as TestSymbol).name)) }

    val contents = assertInstanceOf<CopyReferenceTransferable>(copyReference(TestSymbol("bar")))

    assertEquals("bar", contents.getTransferData(DataFlavor.stringFlavor))
    assertEquals(TestCopyReferenceTarget("bar"), readAction { contents.target })
  }

  @Test
  fun `removes duplicate targets of multiple symbols`(): Unit = timeoutRunBlocking {
    registerProvider { _, symbol -> listOf(TestCopyReferenceTarget((symbol as TestSymbol).name)) }

    val contents = assertInstanceOf<CopyReferenceTransferable>(copyReference(TestSymbol("bar"), TestSymbol("bar")))

    assertEquals("bar", contents.getTransferData(DataFlavor.stringFlavor))
  }

  @Test
  fun `copies the chosen target when multiple symbols have different targets`(): Unit = timeoutRunBlocking {
    registerProvider { _, symbol -> listOf(TestCopyReferenceTarget((symbol as TestSymbol).name)) }

    UiInterceptors.registerPossible(disposable, ChooserInterceptor(null, ".*reference='baz'.*"))

    val contents = assertInstanceOf<CopyReferenceTransferable>(copyReference(TestSymbol("bar"), TestSymbol("baz")))

    assertEquals("baz", contents.getTransferData(DataFlavor.stringFlavor))
  }

  @Test
  fun `falls back to the target of the symbol when the provider returns no targets`(): Unit = timeoutRunBlocking {
    registerProvider { _, _ -> emptyList() }

    val contents = assertInstanceOf<CopyReferenceTransferable>(copyReference(TestCopyReferenceSymbol("symbol")))

    assertEquals("symbol", contents.getTransferData(DataFlavor.stringFlavor))
  }

  private fun registerProvider(provider: SymbolCopyReferenceTargetProvider) {
    SYMBOL_COPY_REFERENCE_TARGET_PROVIDER_EP.point.registerExtension(provider, disposable)
  }

  private suspend fun copyReference(vararg symbols: Symbol): Transferable = withContext(Dispatchers.EDT) {
    fixture.configureByText("SymbolCopyReferenceTargetProviderTest.txt", "")

    CopyPasteManager.getInstance().setContents(StringSelection(""))
    val dataContext = CustomizedDataContext.withSnapshot(EditorUtil.getEditorDataContext(fixture.editor)) { sink ->
      sink[CommonDataKeys.SYMBOLS] = symbols.toList()
    }
    val action = ActionManager.getInstance().getAction(IdeActions.ACTION_COPY_REFERENCE)
    fixture.performEditorAction(IdeActions.ACTION_COPY_REFERENCE, TestActionEvent.createTestEvent(action, dataContext))
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion()

    CopyPasteManager.getInstance().contents!!
  }

  private data class TestSymbol(val name: String) : Symbol {
    override fun createPointer(): Pointer<TestSymbol> = Pointer.hardPointer(this)
  }

  private data class TestCopyReferenceSymbol(val name: String) : CopyReferenceSymbol {
    override fun createPointer(): Pointer<TestCopyReferenceSymbol> = Pointer.hardPointer(this)

    override fun getCopyReferenceTarget(project: Project): CopyReferenceTarget = TestCopyReferenceTarget(name)
  }

  private data class TestCopyReferenceTarget(private val text: String) : CopyReferenceTarget {
    override fun createPointer(): Pointer<TestCopyReferenceTarget> = Pointer.hardPointer(this)

    override fun getReference(): String = text

    override fun presentation(): TargetPresentation = TargetPresentation.builder(text).presentation()
  }
}
