// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DataMap
import com.intellij.openapi.application.readAction
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.common.configureServerSession
import com.intellij.platform.lsp.common.fakeLspIntegrationFixture
import com.intellij.platform.lsp.impl.features.documentSymbol.LspFileBreadcrumbsCollector
import com.intellij.platform.lsp.impl.features.documentSymbol.LspNavBarModelExtension
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import kotlinx.coroutines.CoroutineScope
import org.eclipse.lsp4j.DocumentSymbol
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.SymbolKind
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
internal class LspNavBarModelExtensionTest {
  companion object {
    private val tempDirFixture = tempPathFixture()
    private val projectFixture = projectFixture(tempDirFixture, openAfterCreation = true)
    private val project by projectFixture

    @Suppress("unused")
    private val moduleFixture = projectFixture.moduleFixture(tempDirFixture, addPathToSourceRoot = true)
  }

  private val codeInsightFixture by codeInsightFixture(projectFixture, tempDirFixture)

  @Suppress("unused")
  private val fakeLspIntegration by projectFixture.fakeLspIntegrationFixture(
    configureServerCapabilities = {
      documentSymbolProvider = Either.forLeft(true)
    },
  )

  private val extension = LspNavBarModelExtension()

  @Test
  fun `leaf element is the innermost symbol around the caret`() = timeoutRunBlocking {
    val psiFile = codeInsightFixture.configureByText("navbar1.txt", "class C { fun f<caret>oo() }")
    expectDocumentSymbols(psiFile.virtualFile)

    readAction {
      val leaf = extension.getLeafElement(editorDataMap())!!
      assertEquals("foo", leaf.name())
      assertEquals("C", leaf.parent.name())
      assertSame(psiFile, leaf.parent.parent)
      assertEquals("C", extension.getParent(leaf).name())
    }
  }

  @Test
  fun `children of the file and of a symbol are the document symbols`() = timeoutRunBlocking {
    val psiFile = codeInsightFixture.configureByText("navbar2.txt", "class C { fun f<caret>oo() }")
    expectDocumentSymbols(psiFile.virtualFile)

    readAction {
      val topLevel = children(psiFile)
      assertEquals(listOf("C"), topLevel.map { it.name() })
      assertEquals(listOf("foo"), children(topLevel.single()).map { it.name() })
    }
  }

  @Test
  fun `editor breadcrumbs are hidden when the navigation bar shows the symbols`() = timeoutRunBlocking {
    val virtualFile = codeInsightFixture.configureByText("navbar3.txt", "class C").virtualFile
    configureServerSession(project, virtualFile)

    readAction {
      val collector = LspFileBreadcrumbsCollector(project)
      assertTrue(collector.handlesFile(virtualFile))
      assertFalse(collector.isShownInEditor(virtualFile))
    }
  }

  @Test
  fun `editor breadcrumbs are shown when the registry key is off`(@TestDisposable disposable: Disposable) = timeoutRunBlocking {
    val virtualFile = codeInsightFixture.configureByText("navbar4.txt", "class C").virtualFile
    configureServerSession(project, virtualFile)
    Registry.get("lsp.navbar.members.hide.editor.breadcrumbs").setValue(false, disposable)

    readAction {
      val collector = LspFileBreadcrumbsCollector(project)
      assertTrue(collector.handlesFile(virtualFile))
      assertTrue(collector.isShownInEditor(virtualFile))
    }
  }

  private suspend fun CoroutineScope.expectDocumentSymbols(virtualFile: VirtualFile) {
    val serverSession = configureServerSession(project, virtualFile)
    val fileUri = serverSession.fileUri(virtualFile)
    serverSession.expectRequest(serverSession.DOCUMENT_SYMBOL, { it.textDocument.uri == fileUri }) {
      val method = DocumentSymbol("foo", SymbolKind.Method, range(10, 20), range(14, 17))
      val clazz = DocumentSymbol("C", SymbolKind.Class, range(0, 21), range(6, 7)).apply {
        children = mutableListOf(method)
      }
      listOf(Either.forRight(clazz))
    }
  }

  private fun children(element: PsiElement): List<PsiElement> {
    val children = mutableListOf<PsiElement>()
    extension.processChildren(element, null) { children.add(it as PsiElement) }
    return children
  }

  private fun editorDataMap(): DataMap {
    val editor = codeInsightFixture.editor
    val psiFile = codeInsightFixture.file
    return object : DataMap {
      @Suppress("UNCHECKED_CAST")
      override fun <T : Any> get(key: DataKey<T>): T? = when (key) {
        CommonDataKeys.EDITOR -> editor as T
        CommonDataKeys.PSI_FILE -> psiFile as T
        else -> null
      }
    }
  }

  private fun PsiElement?.name(): String? = (this as? PsiNamedElement)?.name

  private fun range(startChar: Int, endChar: Int): Range = Range(Position(0, startChar), Position(0, endChar))
}
