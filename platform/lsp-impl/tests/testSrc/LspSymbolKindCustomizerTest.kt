// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.readAction
import com.intellij.platform.lsp.api.customization.LspCustomization
import com.intellij.platform.lsp.api.customization.LspSymbolKindCustomizer
import com.intellij.platform.lsp.common.CLASS_WITH_METHOD_TEXT
import com.intellij.platform.lsp.common.classWithMethodSymbols
import com.intellij.platform.lsp.common.crumbsAtCaret
import com.intellij.platform.lsp.common.fakeLspIntegrationFixture
import com.intellij.platform.lsp.common.navBarChildren
import com.intellij.platform.lsp.common.navBarIcon
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.testFramework.StructureViewComponentTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.lsp4j.SymbolKind
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import javax.swing.Icon

@TestApplication
internal class LspSymbolKindCustomizerTest {
  companion object {
    private val tempDirFixture = tempPathFixture()
    private val projectFixture = projectFixture(tempDirFixture, openAfterCreation = true)

    @Suppress("unused")
    private val moduleFixture = projectFixture.moduleFixture(tempDirFixture, addPathToSourceRoot = true)

    private val SUPPORTED_KINDS = listOf(SymbolKind.Class, SymbolKind.Method)
  }

  private val codeInsightFixture by codeInsightFixture(projectFixture, tempDirFixture)

  private val fakeLspIntegration by projectFixture.fakeLspIntegrationFixture(
    lspCustomization = object : LspCustomization() {
      override val symbolKindCustomizer: LspSymbolKindCustomizer = object : LspSymbolKindCustomizer() {
        override fun getIcon(kind: SymbolKind): Icon? = if (kind == SymbolKind.Method) AllIcons.Nodes.Favorite else super.getIcon(kind)

        override val supportedKinds: List<SymbolKind> = SUPPORTED_KINDS
      }
    },
    configureServerCapabilities = {
      documentSymbolProvider = Either.forLeft(true)
    },
  )

  private suspend fun expectDocumentSymbols(fileName: String) {
    val file = codeInsightFixture.configureByText(fileName, CLASS_WITH_METHOD_TEXT).virtualFile
    val serverSession = fakeLspIntegration.serverSession(file)
    serverSession.expectRequest(serverSession.DOCUMENT_SYMBOL) { classWithMethodSymbols() }
  }

  @Test
  fun `crumbs use the custom icon`() = timeoutRunBlocking {
    expectDocumentSymbols("kind1.txt")

    val icons = codeInsightFixture.crumbsAtCaret().map { it.icon }

    assertEquals(listOf(AllIcons.Nodes.Class, AllIcons.Nodes.Favorite), icons)
  }

  @Test
  fun `navigation bar members use the custom icon`() = timeoutRunBlocking {
    expectDocumentSymbols("kind2.txt")
    val psiFile = codeInsightFixture.file

    val icons = readAction {
      val clazz = navBarChildren(psiFile).single()
      val method = navBarChildren(clazz).single()
      listOf(navBarIcon(clazz), navBarIcon(method))
    }

    assertEquals(listOf(AllIcons.Nodes.Class, AllIcons.Nodes.Favorite), icons)
  }

  @Test
  fun `structure view nodes use the custom icon`() = timeoutRunBlocking {
    expectDocumentSymbols("kind3.txt")

    var icons: List<Icon?> = emptyList()
    withContext(Dispatchers.Default) {
      StructureViewComponentTestUtil.testStructureView(codeInsightFixture) { component ->
        val clazz = component.treeModel.root.children.single()
        icons = listOf(clazz.presentation.getIcon(false), clazz.children.single().presentation.getIcon(false))
      }
    }

    assertEquals(listOf(AllIcons.Nodes.Class, AllIcons.Nodes.Favorite), icons)
  }

  @Test
  fun `client capabilities list the supported kinds for document and workspace symbols`() = timeoutRunBlocking {
    val file = codeInsightFixture.configureByText("kind4.txt", "class C").virtualFile
    fakeLspIntegration.serverSession(file)

    val capabilities = fakeLspIntegration.client.descriptor.clientCapabilities

    assertEquals(SUPPORTED_KINDS, capabilities.textDocument.documentSymbol.symbolKind.valueSet)
    assertEquals(SUPPORTED_KINDS, capabilities.workspace.symbol.symbolKind.valueSet)
  }
}
