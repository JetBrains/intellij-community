// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp

import com.intellij.ide.util.gotoByName.ChooseByNamePopup
import com.intellij.ide.util.gotoByName.GotoClassModel2
import com.intellij.ide.util.gotoByName.GotoSymbolModel2
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.application.EDT
import com.intellij.platform.lsp.common.configureServerSession
import com.intellij.platform.lsp.common.fakeLspServerProviderFixture
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.psi.PsiElement
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.SymbolInformation
import org.eclipse.lsp4j.SymbolKind
import org.eclipse.lsp4j.WorkspaceSymbol
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull

@TestApplication
internal class LspWorkspaceSymbolTest {
  companion object {
    private val tempDirFixture = tempPathFixture()
    private val projectFixture = projectFixture(tempDirFixture, openAfterCreation = true)
    private val project by projectFixture

    @Suppress("unused")
    private val moduleFixture = projectFixture.moduleFixture(tempDirFixture, addPathToSourceRoot = true)
  }

  private val codeInsightFixture by codeInsightFixture(projectFixture, tempDirFixture)

  @Suppress("unused")
  private val fakeLspServerProvider by projectFixture.fakeLspServerProviderFixture(
    configureServerCapabilities = {
      workspaceSymbolProvider = Either.forLeft(true)
    },
  )

  @Nested
  inner class GotoSymbol {
    @Test
    fun `LSP server returns SymbolInformation`() = timeoutRunBlocking {
      // given
      val virtualFile = codeInsightFixture.configureByText("test.txt", "").virtualFile
      val serverSession = configureServerSession(project, virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)

      serverSession.expectRequest(serverSession.WORKSPACE_SYMBOL, { it.query == "MyClass" }) {
        Either.forLeft(listOf(
          SymbolInformation().apply {
            name = "MyClass"
            kind = SymbolKind.Class
            location = Location(fileUri, Range(Position(0, 0), Position(0, 7)))
          }
        ))
      }

      // when
      val model = GotoSymbolModel2(project, codeInsightFixture.testRootDisposable)
      val popup = withContext(Dispatchers.EDT) {
        ChooseByNamePopup.createPopup(project, model, null as PsiElement?, "")
      }

      try {
        val results = withContext(Dispatchers.EDT) {
          popup.calcPopupElements("MyClass", false)
        }

        // then
        serverSession.awaitExpected()
        assertEquals(1, results.size)

        val navigationItem = results[0] as NavigationItem
        assertEquals("MyClass", navigationItem.name)

        val presentation = navigationItem.presentation
        assertNotNull(presentation)
        assertEquals("MyClass", presentation.presentableText)
        assertEquals("test.txt", presentation.locationString)
      }
      finally {
        withContext(Dispatchers.EDT) {
          popup.close(false)
        }
      }
    }

    @Test
    fun `LSP server returns WorkspaceSymbol`() = timeoutRunBlocking {
      // given
      val virtualFile = codeInsightFixture.configureByText("workspace_symbol.txt", "").virtualFile
      val serverSession = configureServerSession(project, virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)

      serverSession.expectRequest(serverSession.WORKSPACE_SYMBOL, { it.query == "MyFunction" }) {
        Either.forRight(listOf(
          WorkspaceSymbol().apply {
            name = "MyFunction"
            kind = SymbolKind.Function
            location = Either.forLeft(Location(fileUri, Range(Position(1, 0), Position(1, 10))))
          }
        ))
      }

      // when
      val model = GotoSymbolModel2(project, codeInsightFixture.testRootDisposable)
      val popup = withContext(Dispatchers.EDT) {
        ChooseByNamePopup.createPopup(project, model, null as PsiElement?, "")
      }

      try {
        val results = withContext(Dispatchers.EDT) {
          popup.calcPopupElements("MyFunction", false)
        }

        // then
        serverSession.awaitExpected()
        assertEquals(1, results.size)

        val navigationItem = results[0] as NavigationItem
        assertEquals("MyFunction", navigationItem.name)

        val presentation = navigationItem.presentation
        assertNotNull(presentation)
        assertEquals("MyFunction", presentation.presentableText)
        assertEquals("workspace_symbol.txt", presentation.locationString)
      }
      finally {
        withContext(Dispatchers.EDT) {
          popup.close(false)
        }
      }
    }

    @Test
    fun `LSP server returns multiple WorkspaceSymbol results`() = timeoutRunBlocking {
      // given
      val virtualFile = codeInsightFixture.configureByText("trigger.txt", "").virtualFile
      val file1 = codeInsightFixture.addFileToProject("request.txt", "").virtualFile
      val file2 = codeInsightFixture.addFileToProject("response.txt", "").virtualFile
      val file3 = codeInsightFixture.addFileToProject("error.txt", "").virtualFile
      val serverSession = configureServerSession(project, virtualFile)

      serverSession.expectRequest(serverSession.WORKSPACE_SYMBOL, { it.query == "Handler" }) {
        Either.forRight(listOf(
          WorkspaceSymbol().apply {
            name = "RequestHandler"
            kind = SymbolKind.Class
            location = Either.forLeft(
              Location(
                serverSession.fileUri(file1),
                Range(Position(0, 0), Position(0, 14))
              )
            )
          },
          WorkspaceSymbol().apply {
            name = "ResponseHandler"
            kind = SymbolKind.Class
            location = Either.forLeft(
              Location(
                serverSession.fileUri(file2),
                Range(Position(5, 0), Position(5, 15))
              )
            )
          },
          WorkspaceSymbol().apply {
            name = "ErrorHandler"
            kind = SymbolKind.Function
            location = Either.forLeft(
              Location(
                serverSession.fileUri(file3),
                Range(Position(10, 0), Position(10, 12))
              )
            )
          }
        ))
      }

      // when
      val model = GotoSymbolModel2(project, codeInsightFixture.testRootDisposable)
      val popup = withContext(Dispatchers.EDT) {
        ChooseByNamePopup.createPopup(project, model, null as PsiElement?, "")
      }

      try {
        val results = withContext(Dispatchers.EDT) {
          popup.calcPopupElements("Handler", false)
        }

        // then
        serverSession.awaitExpected()
        assertEquals(3, results.size)

        val sortedResults = results.sortedBy { (it as NavigationItem).name }

        val navigationItem1 = sortedResults[0] as NavigationItem
        assertEquals("ErrorHandler", navigationItem1.name)
        assertEquals("ErrorHandler", navigationItem1.presentation?.presentableText)
        assertEquals("error.txt", navigationItem1.presentation?.locationString)

        val navigationItem2 = sortedResults[1] as NavigationItem
        assertEquals("RequestHandler", navigationItem2.name)
        assertEquals("RequestHandler", navigationItem2.presentation?.presentableText)
        assertEquals("request.txt", navigationItem2.presentation?.locationString)

        val navigationItem3 = sortedResults[2] as NavigationItem
        assertEquals("ResponseHandler", navigationItem3.name)
        assertEquals("ResponseHandler", navigationItem3.presentation?.presentableText)
        assertEquals("response.txt", navigationItem3.presentation?.locationString)
      }
      finally {
        withContext(Dispatchers.EDT) {
          popup.close(false)
        }
      }
    }

    @Test
    fun `LSP server returns nothing`() = timeoutRunBlocking {
      // given
      val virtualFile = codeInsightFixture.configureByText("empty_result.txt", "").virtualFile
      val serverSession = configureServerSession(project, virtualFile)

      serverSession.expectRequest(serverSession.WORKSPACE_SYMBOL, { it.query == "NonExistent" }) {
        Either.forLeft(emptyList())
      }

      // when
      val model = GotoSymbolModel2(project, codeInsightFixture.testRootDisposable)
      val popup = withContext(Dispatchers.EDT) {
        ChooseByNamePopup.createPopup(project, model, null as PsiElement?, "")
      }

      try {
        val results = withContext(Dispatchers.EDT) {
          popup.calcPopupElements("NonExistent", false)
        }

        // then
        serverSession.awaitExpected()
        assertEquals(0, results.size)
      }
      finally {
        withContext(Dispatchers.EDT) {
          popup.close(false)
        }
      }
    }
  }

  @Nested
  inner class GotoClass {
    @Test
    fun `LSP server returns multiple class symbols from different files`() = timeoutRunBlocking {
      // given
      val virtualFile = codeInsightFixture.configureByText("trigger.txt", "").virtualFile
      val file1 = codeInsightFixture.addFileToProject("model.txt", "").virtualFile
      val file2 = codeInsightFixture.addFileToProject("controller.txt", "").virtualFile
      val file3 = codeInsightFixture.addFileToProject("service.txt", "").virtualFile
      val serverSession = configureServerSession(project, virtualFile)

      serverSession.expectRequest(serverSession.WORKSPACE_SYMBOL, { it.query == "User" }) {
        Either.forRight(listOf(
          WorkspaceSymbol().apply {
            name = "UserModel"
            kind = SymbolKind.Class
            location = Either.forLeft(
              Location(
                serverSession.fileUri(file1),
                Range(Position(0, 0), Position(0, 9))
              )
            )
          },
          WorkspaceSymbol().apply {
            name = "UserController"
            kind = SymbolKind.Class
            location = Either.forLeft(
              Location(
                serverSession.fileUri(file2),
                Range(Position(5, 0), Position(5, 14))
              )
            )
          },
          WorkspaceSymbol().apply {
            name = "UserService"
            kind = SymbolKind.Interface
            location = Either.forLeft(
              Location(
                serverSession.fileUri(file3),
                Range(Position(10, 0), Position(10, 11))
              )
            )
          },
          WorkspaceSymbol().apply {
            name = "getUser"
            kind = SymbolKind.Function
            location = Either.forLeft(
              Location(
                serverSession.fileUri(file3),
                Range(Position(11, 0), Position(11, 11))
              )
            )
          }
        ))
      }

      // when
      val model = GotoClassModel2(project)
      val popup = withContext(Dispatchers.EDT) {
        ChooseByNamePopup.createPopup(project, model, null as PsiElement?, "")
      }

      try {
        val results = withContext(Dispatchers.EDT) {
          popup.calcPopupElements("User", false)
        }

        // then
        serverSession.awaitExpected()
        assertEquals(3, results.size)

        val sortedResults = results.sortedBy { (it as NavigationItem).name }

        val navigationItem1 = sortedResults[0] as NavigationItem
        assertEquals("UserController", navigationItem1.name)
        assertEquals("UserController", navigationItem1.presentation?.presentableText)
        assertEquals("controller.txt", navigationItem1.presentation?.locationString)

        val navigationItem2 = sortedResults[1] as NavigationItem
        assertEquals("UserModel", navigationItem2.name)
        assertEquals("UserModel", navigationItem2.presentation?.presentableText)
        assertEquals("model.txt", navigationItem2.presentation?.locationString)

        val navigationItem3 = sortedResults[2] as NavigationItem
        assertEquals("UserService", navigationItem3.name)
        assertEquals("UserService", navigationItem3.presentation?.presentableText)
        assertEquals("service.txt", navigationItem3.presentation?.locationString)
      }
      finally {
        withContext(Dispatchers.EDT) {
          popup.close(false)
        }
      }
    }
  }
}
