// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp

import com.intellij.openapi.actionSystem.IdeActions.ACTION_GOTO_TYPE_DECLARATION
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
 * [textDocument/typeDefinition](https://microsoft.github.io/language-server-protocol/specification/#textDocument_typeDefinition)
 * behind [Go To Type Declaration][com.intellij.codeInsight.navigation.actions.GotoTypeDeclarationAction].
 */
@TestApplication
internal class LspGotoTypeDefinitionTest {
  companion object {
    private val tempDirFixture = tempPathFixture()
    private val projectFixture = projectFixture(tempDirFixture, openAfterCreation = true)
    private val project by projectFixture

    @Suppress("unused")
    private val moduleFixture = projectFixture.moduleFixture(tempDirFixture, addPathToSourceRoot = true)

    /** The type of `bar` at offsets 4..7 is declared as `foo` at offsets 0..3. */
    private const val TEXT: String = "foo b<caret>ar"
    private val TYPE_DEFINITION_RANGE: Range = Range(Position(0, 0), Position(0, 3))
    private val REFERENCE_RANGE: Range = Range(Position(0, 4), Position(0, 7))
    private const val OFFSET_IN_REFERENCE: Int = 5
  }

  private val codeInsightFixture by codeInsightFixture(projectFixture, tempDirFixture)

  @Suppress("unused")
  private val fakeLspIntegration by projectFixture.fakeLspIntegrationFixture(
    configureServerCapabilities = {
      typeDefinitionProvider = Either.forLeft(true)
    },
  )

  private fun FakeLspServerSession.expectTypeDefinition(fileUri: String, locationLinks: List<LocationLink>) =
    expectRequest(TYPE_DEFINITION, { it.textDocument.uri == fileUri && it.position == Position(0, OFFSET_IN_REFERENCE) }) {
      Either.forRight(locationLinks)
    }

  private fun typeDefinitionInThisFile(fileUri: String): List<LocationLink> =
    listOf(LocationLink(fileUri, TYPE_DEFINITION_RANGE, TYPE_DEFINITION_RANGE, REFERENCE_RANGE))

  @Nested
  inner class GoToTypeDeclarationAction {
    @Test
    fun `given type definition when go to type declaration is invoked then the caret moves to the type definition`() =
      timeoutRunBlocking {
        val virtualFile = codeInsightFixture.configureByText("test.txt", TEXT).virtualFile
        val serverSession = configureServerSession(project, virtualFile)
        val fileUri = serverSession.fileUri(virtualFile)
        serverSession.expectTypeDefinition(fileUri, typeDefinitionInThisFile(fileUri))

        withContext(Dispatchers.EDT) {
          codeInsightFixture.performEditorAction(ACTION_GOTO_TYPE_DECLARATION)
        }

        serverSession.awaitExpected()
        assertEquals(0, withContext(Dispatchers.EDT) { codeInsightFixture.caretOffset })
      }

    @Test
    fun `given no type definition when go to type declaration is invoked then the caret stays put`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("test.txt", TEXT).virtualFile
      val serverSession = configureServerSession(project, virtualFile)
      val fileUri = serverSession.fileUri(virtualFile)
      serverSession.expectTypeDefinition(fileUri, emptyList())

      withContext(Dispatchers.EDT) {
        codeInsightFixture.performEditorAction(ACTION_GOTO_TYPE_DECLARATION)
      }

      serverSession.awaitExpected()
      assertEquals(OFFSET_IN_REFERENCE, withContext(Dispatchers.EDT) { codeInsightFixture.caretOffset })
    }
  }
}
