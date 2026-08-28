// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp

import com.intellij.codeInsight.navigation.GotoImplementationHandler
import com.intellij.codeInsight.navigation.GotoTargetHandler
import com.intellij.codeInsight.navigation.actions.GotoImplementationAction
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.components.service
import com.intellij.platform.lsp.api.customization.LspCustomization
import com.intellij.platform.lsp.api.customization.LspGoToImplementationCustomizer
import com.intellij.platform.lsp.api.customization.LspGoToImplementationDisabled
import com.intellij.platform.lsp.common.configureServerSession
import com.intellij.platform.lsp.common.fakeLspServerProviderFixture
import com.intellij.platform.lsp.impl.features.navigation.CurrentActionHolder
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.psi.PsiNamedElement
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.LocationLink
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull

@TestApplication
internal class LspGotoImplementationTest {
  companion object {
    private val tempDirFixture = tempPathFixture()
    private val projectFixture = projectFixture(tempDirFixture, openAfterCreation = true)
    private val project by projectFixture

    @Suppress("unused")
    private val moduleFixture = projectFixture.moduleFixture(tempDirFixture, addPathToSourceRoot = true)
  }

  private val codeInsightFixture by codeInsightFixture(projectFixture, tempDirFixture)

  /**
   * Runs the same computation as the "Go To Implementation" action, without the target chooser popup.
   * The action gate in `LspImplementationDeclarationSearcher` reads [CurrentActionHolder],
   * so the holder is set the same way `CurrentActionListener` sets it during the real action.
   */
  private suspend fun gotoImplementationData(): GotoTargetHandler.GotoData? {
    return withContext(Dispatchers.EDT) {
      val holder = service<CurrentActionHolder>()
      holder.currentActionClass = GotoImplementationAction::class.java
      try {
        GotoImplementationHandler().getSourceAndTargetElements(codeInsightFixture.editor, codeInsightFixture.file)
      }
      finally {
        holder.currentActionClass = null
      }
    }
  }

  @AfterEach
  fun waitForAsyncTaskCompletion() {
    // Wait for all pending non-blocking read actions and their EDT continuations (e.g., write actions
    // scheduled by LspDocumentListener) to complete before fixture teardown, so that background tasks
    // don't race with resource cleanup and tests can finish gracefully.
    timeoutRunBlocking {
      withContext(Dispatchers.EDT) {
        NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
      }
    }
  }

  @Nested
  inner class ImplementationSupported {
    @Suppress("unused")
    private val fakeLspServerProvider by projectFixture.fakeLspServerProviderFixture(
      configureServerCapabilities = {
        implementationProvider = Either.forLeft(true)
      },
    )

    @Test
    fun `server response as Location list becomes the navigation target`() = timeoutRunBlocking {
      // given
      val implFile = codeInsightFixture.addFileToProject("impl.txt", "class FooImpl : Foo").virtualFile
      val virtualFile = codeInsightFixture.configureByText("test.txt", "interface F<caret>oo").virtualFile
      val serverSession = configureServerSession(project, virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)
      val implUri = serverSession.fileUri(implFile)

      serverSession.expectRequest(serverSession.IMPLEMENTATION, {
        it.textDocument.uri == fileUri && it.position == Position(0, 11)
      }) {
        Either.forLeft(listOf(Location(implUri, Range(Position(0, 6), Position(0, 13)))))
      }

      // when
      val gotoData = gotoImplementationData()

      // then
      assertNotNull(gotoData, "The action should get a source element and search for targets")
      assertEquals("Foo", (gotoData.source as PsiNamedElement).name, "The source element name is the word at the caret")
      assertEquals(1, gotoData.targets.size, "Actual targets: ${gotoData.targets.contentToString()}")
      val target = gotoData.targets[0]
      assertEquals(implFile, target.containingFile.virtualFile)
      assertEquals(6, target.textOffset, "Navigation should go to the start of the target selection range")
      serverSession.awaitExpected()
    }

    @Test
    fun `caret at the end of the word sends the position inside the word`() = timeoutRunBlocking {
      // given
      val implFile = codeInsightFixture.addFileToProject("impl.txt", "class FooImpl : Foo").virtualFile
      val virtualFile = codeInsightFixture.configureByText("test.txt", "interface Foo<caret> {}").virtualFile
      val serverSession = configureServerSession(project, virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)
      val implUri = serverSession.fileUri(implFile)

      // The platform adjusts the caret offset 13 to 12, the last character of `Foo`.
      serverSession.expectRequest(serverSession.IMPLEMENTATION, {
        it.textDocument.uri == fileUri && it.position == Position(0, 12)
      }) {
        Either.forLeft(listOf(Location(implUri, Range(Position(0, 6), Position(0, 13)))))
      }

      // when
      val gotoData = gotoImplementationData()

      // then
      assertNotNull(gotoData, "The action should work when the caret is right after the word")
      assertEquals("Foo", (gotoData.source as PsiNamedElement).name, "The source element name is the word before the caret")
      assertEquals(1, gotoData.targets.size, "Actual targets: ${gotoData.targets.contentToString()}")
      serverSession.awaitExpected()
    }

    @Test
    fun `server response as LocationLink list becomes the navigation target`() = timeoutRunBlocking {
      // given
      val implFile = codeInsightFixture.addFileToProject("impl.txt", "class FooImpl : Foo").virtualFile
      val virtualFile = codeInsightFixture.configureByText("test.txt", "interface F<caret>oo").virtualFile
      val serverSession = configureServerSession(project, virtualFile)
      val implUri = serverSession.fileUri(implFile)

      serverSession.expectRequest(serverSession.IMPLEMENTATION) {
        Either.forRight(listOf(LocationLink(implUri, Range(Position(0, 0), Position(0, 19)), Range(Position(0, 6), Position(0, 13)))))
      }

      // when
      val gotoData = gotoImplementationData()

      // then
      assertNotNull(gotoData)
      assertEquals(1, gotoData.targets.size, "Actual targets: ${gotoData.targets.contentToString()}")
      val target = gotoData.targets[0]
      assertEquals(implFile, target.containingFile.virtualFile)
      assertEquals(6, target.textOffset, "Navigation should go to the start of the target selection range")
      serverSession.awaitExpected()
    }
  }

  @Nested
  inner class ImplementationNotSupportedByServer {
    @Suppress("unused")
    private val fakeLspServerProvider by projectFixture.fakeLspServerProviderFixture()

    @Test
    fun `no request when the server has no implementation capability`() = timeoutRunBlocking {
      // given
      val virtualFile = codeInsightFixture.configureByText("test.txt", "interface F<caret>oo").virtualFile
      configureServerSession(project, virtualFile)

      // when
      val gotoData = gotoImplementationData()

      // then
      assertNull(gotoData, "The action should not get a source element in a plain text file")
    }
  }

  @Nested
  inner class ImplementationDisabledByCustomizer {
    @Suppress("unused")
    private val fakeLspServerProvider by projectFixture.fakeLspServerProviderFixture(
      lspCustomization = object : LspCustomization() {
        override val goToImplementationCustomizer: LspGoToImplementationCustomizer = LspGoToImplementationDisabled
      },
      configureServerCapabilities = {
        implementationProvider = Either.forLeft(true)
      },
    )

    @Test
    fun `no request when the customizer disables the feature`() = timeoutRunBlocking {
      // given
      val virtualFile = codeInsightFixture.configureByText("test.txt", "interface F<caret>oo").virtualFile
      configureServerSession(project, virtualFile)

      // when
      val gotoData = gotoImplementationData()

      // then
      assertNull(gotoData, "The action should not get a source element when the customizer disables the feature")
    }
  }
}
