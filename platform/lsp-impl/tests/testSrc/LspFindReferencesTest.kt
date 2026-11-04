// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp

import com.intellij.find.actions.FindUsagesAction
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.platform.lsp.api.customization.LspCustomization
import com.intellij.platform.lsp.api.customization.LspFindReferencesCustomizer
import com.intellij.platform.lsp.api.customization.LspFindReferencesDisabled
import com.intellij.platform.lsp.common.fakeLspIntegrationFixture
import com.intellij.platform.lsp.common.nullResponse
import com.intellij.platform.lsp.impl.features.usages.LspSearchTarget
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.usages.UsageViewManager
import com.intellij.usages.impl.UsageViewImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@TestApplication
internal class LspFindReferencesTest {
  companion object {
    private val tempDirFixture = tempPathFixture()
    private val projectFixture = projectFixture(tempDirFixture, openAfterCreation = true)

    @Suppress("unused")
    private val moduleFixture = projectFixture.moduleFixture(tempDirFixture, addPathToSourceRoot = true)
  }

  private val codeInsightFixture by codeInsightFixture(projectFixture, tempDirFixture)

  /** The search targets that the Find Usages and Show Usages actions get from the editor. */
  private suspend fun lspSearchTargets(): List<LspSearchTarget> = withContext(Dispatchers.EDT) {
    val dataContext = EditorUtil.getEditorDataContext(codeInsightFixture.editor)
    FindUsagesAction.SEARCH_TARGETS.getData(dataContext).orEmpty().filterIsInstance<LspSearchTarget>()
  }

  /**
   * Searches for the usages of the LSP search target at the caret, as Find Usages does, and returns the text of the usage view tree.
   * The project name in the text is replaced with `<project>`.
   */
  private suspend fun findUsages(): String {
    val target = lspSearchTargets().single()
    val text = withContext(Dispatchers.Default) { codeInsightFixture.getUsageViewTreeTextRepresentation(target) }
    return normalized(text)
  }

  /**
   * Runs the Find Usages action in the editor and returns the text of the usage view tree that the action opens.
   * The action opens no usage view when there are no usages, and jumps to the usage when there is one,
   * so give the server at least two usages, and use [findUsages] when there are none.
   */
  private suspend fun findUsagesUsingAction(): String = withContext(Dispatchers.Default) {
    codeInsightFixture.testFindUsagesUsingAction()
    val usageView = withContext(Dispatchers.EDT) {
      UsageViewManager.getInstance(codeInsightFixture.project).selectedUsageView as UsageViewImpl
    }
    normalized((codeInsightFixture as CodeInsightTestFixtureImpl).getUsageViewTreeTextRepresentation(usageView))
  }

  private fun normalized(usageViewText: String): String =
    usageViewText.replace(codeInsightFixture.project.name, "<project>").lines().joinToString("\n") { it.trimEnd() }.trimEnd()

  /** Updates the Find Usages action with the data context of the editor, as the IDE does before it shows the action. */
  private suspend fun isFindUsagesEnabled(): Boolean = withContext(Dispatchers.EDT) {
    val action = ActionManager.getInstance().getAction(IdeActions.ACTION_FIND_USAGES)
    val event = TestActionEvent.createTestEvent(action, EditorUtil.getEditorDataContext(codeInsightFixture.editor))
    ActionUtil.updateAction(action, event)
    event.presentation.isEnabled
  }

  @Nested
  inner class ReferencesSupported {
    private val fakeLspIntegration by projectFixture.fakeLspIntegrationFixture(
      configureServerCapabilities = {
        referencesProvider = Either.forLeft(true)
      },
    )

    @Test
    fun `usages in the open file and in a file not open in an editor are listed`() = timeoutRunBlocking {
      val otherFile = codeInsightFixture.addFileToProject("other.txt", "use world here").virtualFile
      val virtualFile = codeInsightFixture.configureByText("twoFiles.txt", "hello wor<caret>ld\nworld again").virtualFile
      val serverSession = fakeLspIntegration.serverSession(virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)
      val otherUri = serverSession.fileUri(otherFile)

      serverSession.expectRequest(serverSession.REFERENCES, {
        it.textDocument.uri == fileUri && it.position == Position(0, 9) && it.context.isIncludeDeclaration
      }) {
        listOf(
          Location(fileUri, Range(Position(0, 6), Position(0, 11))),
          Location(fileUri, Range(Position(1, 0), Position(1, 5))),
          Location(otherUri, Range(Position(0, 4), Position(0, 9))),
        )
      }

      val usages = findUsagesUsingAction()

      serverSession.awaitExpected()
      assertEquals("""
        <root> (3)
         Targets
          twoFiles.txt:1:10
         Usages in Project Files (3)
          <project> (3)
            (3)
            other.txt (1)
             1use world here
            twoFiles.txt (2)
             1hello world
             2world again
      """.trimIndent(), usages)
    }

    @Test
    fun `locations in an unknown file or outside the document are skipped`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("skipped.txt", "hello wor<caret>ld\nworld").virtualFile
      val serverSession = fakeLspIntegration.serverSession(virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)
      val unknownUri = fileUri.substringBeforeLast('/') + "/missing.txt"

      serverSession.expectRequest(serverSession.REFERENCES, { it.textDocument.uri == fileUri }) {
        listOf(
          Location(unknownUri, Range(Position(0, 0), Position(0, 5))),
          Location(fileUri, Range(Position(5, 0), Position(5, 3))),
          Location(fileUri, Range(Position(0, 6), Position(0, 11))),
          Location(fileUri, Range(Position(1, 0), Position(1, 5))),
        )
      }

      val usages = findUsagesUsingAction()

      serverSession.awaitExpected()
      assertEquals("""
        <root> (2)
         Targets
          skipped.txt:1:10
         Usages in Project Files (2)
          <project> (2)
            (2)
            skipped.txt (2)
             1hello world
             2world
      """.trimIndent(), usages)
    }

    @Test
    fun `null response gives no usages`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("nullResponse.txt", "hello wor<caret>ld").virtualFile
      val serverSession = fakeLspIntegration.serverSession(virtualFile)

      serverSession.expectRequest(serverSession.REFERENCES) { nullResponse() }

      val text = findUsages()

      serverSession.awaitExpected()
      assertEquals(noUsagesView("nullResponse.txt:1:10"), text)
    }

    @Test
    fun `empty response gives no usages`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("emptyResponse.txt", "hello wor<caret>ld").virtualFile
      val serverSession = fakeLspIntegration.serverSession(virtualFile)

      serverSession.expectRequest(serverSession.REFERENCES) { emptyList() }

      val text = findUsages()

      serverSession.awaitExpected()
      assertEquals(noUsagesView("emptyResponse.txt:1:10"), text)
    }

    @Test
    fun `error response gives no usages, and the next search asks again`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("errorResponse.txt", "hello wor<caret>ld\nworld").virtualFile
      val serverSession = fakeLspIntegration.serverSession(virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)

      serverSession.expectRequest(serverSession.REFERENCES) {
        throw ResponseErrorException(ResponseError(ResponseErrorCode.InternalError, "references failed", null))
      }

      val textAfterError = findUsages()
      serverSession.awaitExpected()

      serverSession.expectRequest(serverSession.REFERENCES) {
        listOf(
          Location(fileUri, Range(Position(0, 6), Position(0, 11))),
          Location(fileUri, Range(Position(1, 0), Position(1, 5))),
        )
      }
      val usagesAfterRetry = findUsagesUsingAction()

      serverSession.awaitExpected()
      assertEquals(noUsagesView("errorResponse.txt:1:10"), textAfterError)
      assertEquals("""
        <root> (2)
         Targets
          errorResponse.txt:1:10
         Usages in Project Files (2)
          <project> (2)
            (2)
            errorResponse.txt (2)
             1hello world
             2world
      """.trimIndent(), usagesAfterRetry)
    }

    @Test
    fun `usage view shows the file name and the one-based position of the search target`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("presentation.txt", "hello\nwor<caret>ld\nworld").virtualFile
      val serverSession = fakeLspIntegration.serverSession(virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)

      serverSession.expectRequest(serverSession.REFERENCES, { it.position == Position(1, 3) }) {
        listOf(
          Location(fileUri, Range(Position(1, 0), Position(1, 5))),
          Location(fileUri, Range(Position(2, 0), Position(2, 5))),
        )
      }

      val enabled = isFindUsagesEnabled()
      val text = findUsagesUsingAction()

      serverSession.awaitExpected()
      assertTrue(enabled, "Find Usages should be enabled at an LSP search target")
      assertEquals("""
        <root> (2)
         Targets
          presentation.txt:2:4
         Usages in Project Files (2)
          <project> (2)
            (2)
            presentation.txt (2)
             2world
             3world
      """.trimIndent(), text)
    }
  }

  @Nested
  inner class ReferencesNotSupportedByServer {
    private val fakeLspIntegration by projectFixture.fakeLspIntegrationFixture()

    @Test
    fun `no search target when the server has no references capability`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("noCapability.txt", "hello wor<caret>ld").virtualFile
      fakeLspIntegration.serverSession(virtualFile)

      val enabled = isFindUsagesEnabled()

      assertFalse(enabled, "Find Usages should be disabled without an LSP search target")
    }
  }

  @Nested
  inner class ReferencesDisabledByCustomizer {
    private val fakeLspIntegration by projectFixture.fakeLspIntegrationFixture(
      lspCustomization = object : LspCustomization() {
        override val findReferencesCustomizer: LspFindReferencesCustomizer = LspFindReferencesDisabled
      },
      configureServerCapabilities = {
        referencesProvider = Either.forLeft(true)
      },
    )

    @Test
    fun `no search target when the customizer disables the feature`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("disabled.txt", "hello wor<caret>ld").virtualFile
      fakeLspIntegration.serverSession(virtualFile)

      val enabled = isFindUsagesEnabled()

      assertFalse(enabled, "Find Usages should be disabled without an LSP search target")
    }
  }
}

private fun noUsagesView(target: String): String = "<root> (0)\n Targets\n  $target"
