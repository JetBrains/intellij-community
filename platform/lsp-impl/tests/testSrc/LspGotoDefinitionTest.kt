// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp

import com.intellij.openapi.actionSystem.IdeActions.ACTION_GOTO_DECLARATION
import com.intellij.openapi.application.EDT
import com.intellij.platform.lsp.common.FakeLspServerSession
import com.intellij.platform.lsp.common.configureServerSession
import com.intellij.platform.lsp.common.fakeLspIntegrationFixture
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.lsp4j.LocationLink
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * [textDocument/definition](https://microsoft.github.io/language-server-protocol/specification/#textDocument_definition)
 * behind [Go To Declaration][com.intellij.codeInsight.navigation.actions.GotoDeclarationAction].
 */
@TestApplication
internal class LspGotoDefinitionTest {
  companion object {
    private val tempDirFixture = tempPathFixture()
    private val projectFixture = projectFixture(tempDirFixture, openAfterCreation = true)
    private val project by projectFixture

    @Suppress("unused")
    private val moduleFixture = projectFixture.moduleFixture(tempDirFixture, addPathToSourceRoot = true)

    /** `bar` at offsets 4..7 refers to `foo` at offsets 0..3. */
    private const val TEXT: String = "foo b<caret>ar"
    private val DEFINITION_RANGE: Range = Range(Position(0, 0), Position(0, 3))
    private val REFERENCE_RANGE: Range = Range(Position(0, 4), Position(0, 7))
    private const val OFFSET_IN_REFERENCE: Int = 5
  }

  private val codeInsightFixture by codeInsightFixture(projectFixture, tempDirFixture)

  @Suppress("unused")
  private val fakeLspIntegration by projectFixture.fakeLspIntegrationFixture(
    configureServerCapabilities = {
      definitionProvider = Either.forLeft(true)
    },
  )

  private fun FakeLspServerSession.expectDefinition(fileUri: String, locationLinks: List<LocationLink>) =
    expectRequest(DEFINITION, { it.textDocument.uri == fileUri && it.position == Position(0, OFFSET_IN_REFERENCE) }) {
      Either.forRight(locationLinks)
    }

  private fun definitionInThisFile(fileUri: String): List<LocationLink> =
    listOf(LocationLink(fileUri, DEFINITION_RANGE, DEFINITION_RANGE, REFERENCE_RANGE))

  @Nested
  inner class GoToDeclarationAction {
    @Test
    fun `given definition when go to declaration is invoked then the caret moves to the definition`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test.txt", TEXT).virtualFile
      val serverSession = configureServerSession(project, virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)
      serverSession.expectDefinition(fileUri, definitionInThisFile(fileUri))

      withContext(Dispatchers.EDT) {
        codeInsightFixture.performEditorAction(ACTION_GOTO_DECLARATION)
      }

      serverSession.awaitExpected()
      assertEquals(0, withContext(Dispatchers.EDT) { codeInsightFixture.caretOffset })
    }

    @Test
    fun `given no definition when go to declaration is invoked then the caret stays put`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test.txt", TEXT).virtualFile
      val serverSession = configureServerSession(project, virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)
      serverSession.expectDefinition(fileUri, emptyList())

      withContext(Dispatchers.EDT) {
        codeInsightFixture.performEditorAction(ACTION_GOTO_DECLARATION)
      }

      serverSession.awaitExpected()
      assertEquals(OFFSET_IN_REFERENCE, withContext(Dispatchers.EDT) { codeInsightFixture.caretOffset })
    }

    @Test
    fun `given a definition pointing at the reference itself when go to declaration is invoked then the caret stays put`() =
      timeoutRunBlocking {
        val virtualFile = codeInsightFixture.configureByText("test.txt", TEXT).virtualFile
        val serverSession = configureServerSession(project, virtualFile)
        val fileUri = serverSession.fileUri(virtualFile)
        serverSession.expectDefinition(fileUri, listOf(LocationLink(fileUri, REFERENCE_RANGE, REFERENCE_RANGE, REFERENCE_RANGE)))

        withContext(Dispatchers.EDT) {
          codeInsightFixture.performEditorAction(ACTION_GOTO_DECLARATION)
        }

        serverSession.awaitExpected()
        assertEquals(OFFSET_IN_REFERENCE, withContext(Dispatchers.EDT) { codeInsightFixture.caretOffset })
      }
  }
}
