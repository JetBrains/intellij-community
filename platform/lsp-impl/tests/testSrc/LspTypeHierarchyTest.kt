// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp

import com.intellij.icons.AllIcons
import com.intellij.ide.hierarchy.HierarchyBrowserBaseEx
import com.intellij.ide.hierarchy.TypeHierarchyBrowserBase
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.customization.LspCustomization
import com.intellij.platform.lsp.api.customization.LspTypeHierarchyCustomizer
import com.intellij.platform.lsp.api.customization.LspTypeHierarchyDisabled
import com.intellij.platform.lsp.api.customization.LspTypeHierarchySupport
import com.intellij.platform.lsp.common.closeHierarchies
import com.intellij.platform.lsp.common.fakeLspIntegrationFixture
import com.intellij.platform.lsp.common.nullResponse
import com.intellij.platform.lsp.common.openHierarchy
import com.intellij.platform.lsp.common.showView
import com.intellij.platform.lsp.common.shownTree
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.SymbolKind
import org.eclipse.lsp4j.TypeHierarchyItem
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * [Type hierarchy](https://microsoft.github.io/language-server-protocol/specification/#textDocument_prepareTypeHierarchy)
 * through the Type Hierarchy action and the browser it opens in the Hierarchy tool window.
 */
@TestApplication
internal class LspTypeHierarchyTest {
  companion object {
    private val tempDirFixture = tempPathFixture()
    private val projectFixture = projectFixture(tempDirFixture, openAfterCreation = true)
    private val project by projectFixture

    @Suppress("unused")
    private val moduleFixture = projectFixture.moduleFixture(tempDirFixture, addPathToSourceRoot = true)

    /** `Base` on line 0, `Impl` extends it on line 1. */
    private const val TEXT: String = "interface Ba<caret>se\nclass Impl : Base"
    private val BASE_POSITION: Position = Position(0, 12)
  }

  private val codeInsightFixture by codeInsightFixture(projectFixture, tempDirFixture)

  @AfterEach
  fun closeHierarchyBrowsers() = timeoutRunBlocking {
    closeHierarchies(project)
  }

  private suspend fun openTypeHierarchy(): HierarchyBrowserBaseEx? = codeInsightFixture.openHierarchy(IdeActions.ACTION_TYPE_HIERARCHY)

  private suspend fun requireTypeHierarchy(): HierarchyBrowserBaseEx =
    checkNotNull(openTypeHierarchy()) { "The Type Hierarchy action should be enabled at the caret" }

  private fun baseItem(uri: String): TypeHierarchyItem =
    TypeHierarchyItem("Base", SymbolKind.Interface, uri, range(0, 0, 0, 14), range(0, 10, 0, 14))

  private fun implItem(uri: String): TypeHierarchyItem =
    TypeHierarchyItem("Impl", SymbolKind.Class, uri, range(1, 0, 1, 17), range(1, 6, 1, 10))

  @Nested
  inner class TypeHierarchySupported {
    private val fakeLspIntegration by typeHierarchyIntegrationFixture()

    @Test
    fun `the subtypes tree root is the prepared item and its children are the subtypes`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("types1.txt", TEXT).virtualFile
      val serverSession = fakeLspIntegration.serverSession(virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)
      serverSession.expectRequest(serverSession.PREPARE_TYPE_HIERARCHY, {
        it.textDocument.uri == fileUri && it.position == BASE_POSITION
      }) {
        listOf(baseItem(fileUri))
      }
      serverSession.expectRequest(serverSession.TYPE_HIERARCHY_SUBTYPES, { it.item.name == "Base" }) {
        listOf(implItem(fileUri))
      }

      val tree = requireTypeHierarchy().shownTree()

      assertEquals("Base\n  Impl", tree.text)
      assertSame(AllIcons.Nodes.Interface, tree.node("Base").descriptor.icon)
      assertSame(AllIcons.Nodes.Class, tree.node("Impl").descriptor.icon)
      serverSession.awaitExpected()
    }

    @Test
    fun `the supertypes tree children are the supertypes`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("types2.txt", "interface Base\nclass Im<caret>pl : Base").virtualFile
      val serverSession = fakeLspIntegration.serverSession(virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)
      // the subtypes view opens first, and each view prepares its own root
      repeat(2) {
        serverSession.expectRequest(serverSession.PREPARE_TYPE_HIERARCHY, { it.position == Position(1, 8) }) {
          listOf(implItem(fileUri).apply { detail = "final" })
        }
      }
      serverSession.expectRequest(serverSession.TYPE_HIERARCHY_SUBTYPES) { emptyList() }
      serverSession.expectRequest(serverSession.TYPE_HIERARCHY_SUPERTYPES, { it.item.name == "Impl" }) {
        listOf(baseItem(fileUri))
      }

      val browser = requireTypeHierarchy()
      assertEquals("Impl : final", browser.shownTree().text)
      browser.showView(TypeHierarchyBrowserBase.getSupertypesHierarchyType())
      val tree = browser.shownTree()

      assertEquals("Impl : final\n  Base", tree.text)
      serverSession.awaitExpected()
    }

    @Test
    fun `the item data goes back to the server unchanged`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("types3.txt", TEXT).virtualFile
      val serverSession = fakeLspIntegration.serverSession(virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)
      val data = mapOf("symbolId" to 7, "path" to listOf("Base"))
      serverSession.expectRequest(serverSession.PREPARE_TYPE_HIERARCHY) {
        listOf(baseItem(fileUri).apply { this.data = data })
      }
      val subtypesRequested = serverSession.expectRequest(serverSession.TYPE_HIERARCHY_SUBTYPES, {
        it.item.data.toString() == """{"symbolId":7,"path":["Base"]}"""
      }) {
        emptyList()
      }

      val tree = requireTypeHierarchy().shownTree()

      assertTrue(subtypesRequested.isCompleted, "typeHierarchy/subtypes should carry the prepared item with its data")
      assertEquals("Base", tree.text)
      serverSession.awaitExpected()
    }

    @Test
    fun `an empty prepare answer gives an empty tree`() = timeoutRunBlocking {
      assertEmptyTree("types4.txt") { emptyList() }
    }

    @Test
    fun `a null prepare answer gives an empty tree`() = timeoutRunBlocking {
      assertEmptyTree("types5.txt") { nullResponse() }
    }

    @Test
    fun `an error prepare answer gives an empty tree`() = timeoutRunBlocking {
      assertEmptyTree("types6.txt") {
        throw ResponseErrorException(ResponseError(ResponseErrorCode.InternalError, "cannot prepare", null))
      }
    }

    private suspend fun assertEmptyTree(fileName: String, prepareAnswer: suspend () -> List<TypeHierarchyItem>) {
      val virtualFile = codeInsightFixture.configureByText(fileName, TEXT).virtualFile
      val serverSession = fakeLspIntegration.serverSession(virtualFile)
      serverSession.expectRequest(serverSession.PREPARE_TYPE_HIERARCHY) { prepareAnswer() }

      val tree = requireTypeHierarchy().shownTree()

      assertEquals("", tree.text)
      serverSession.awaitExpected()
    }
  }

  @Nested
  inner class NoTypeHierarchyCapability {
    private val fakeLspIntegration by projectFixture.fakeLspIntegrationFixture()

    @Test
    fun `the action is disabled when the server has no type hierarchy capability`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("types7.txt", TEXT).virtualFile
      fakeLspIntegration.serverSession(virtualFile)

      assertNull(openTypeHierarchy())
    }
  }

  @Nested
  inner class ShouldAskServerForTypeHierarchyFalse {
    private val fakeLspIntegration by typeHierarchyIntegrationFixture(object : LspTypeHierarchySupport() {
      override fun shouldAskServerForTypeHierarchy(file: VirtualFile): Boolean = false
    })

    @Test
    fun `the action is disabled when the customizer declines the file`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("types8.txt", TEXT).virtualFile
      fakeLspIntegration.serverSession(virtualFile)

      assertNull(openTypeHierarchy())
    }
  }

  @Nested
  inner class TypeHierarchyDisabled {
    private val fakeLspIntegration by typeHierarchyIntegrationFixture(LspTypeHierarchyDisabled)

    @Test
    fun `the action is disabled when the customizer disables the feature`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("types9.txt", TEXT).virtualFile
      fakeLspIntegration.serverSession(virtualFile)

      assertNull(openTypeHierarchy())
    }
  }

  private fun typeHierarchyIntegrationFixture(customizer: LspTypeHierarchyCustomizer = LspTypeHierarchySupport()) =
    projectFixture.fakeLspIntegrationFixture(
      lspCustomization = object : LspCustomization() {
        override val typeHierarchyCustomizer: LspTypeHierarchyCustomizer = customizer
      },
      configureServerCapabilities = {
        typeHierarchyProvider = Either.forLeft(true)
      },
    )

  private fun range(startLine: Int, startChar: Int, endLine: Int, endChar: Int): Range =
    Range(Position(startLine, startChar), Position(endLine, endChar))
}
