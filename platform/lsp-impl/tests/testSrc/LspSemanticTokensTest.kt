// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp

import com.intellij.openapi.application.EDT
import com.intellij.openapi.command.writeCommandAction
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.platform.lsp.api.customization.LspCustomization
import com.intellij.platform.lsp.api.customization.LspSemanticTokensCustomizer
import com.intellij.platform.lsp.api.customization.LspSemanticTokensDisabled
import com.intellij.platform.lsp.api.customization.LspSemanticTokensSupport
import com.intellij.platform.lsp.common.FakeLspIntegration
import com.intellij.platform.lsp.common.FakeLspServerSession
import com.intellij.platform.lsp.common.SpaceTokenizingFileType
import com.intellij.platform.lsp.common.fakeLspIntegrationFixture
import com.intellij.platform.lsp.common.spaceTokenizingLanguageFixture
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.psi.PsiFile
import com.intellij.testFramework.ExpectedHighlightingData
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntilAssertSucceeds
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.eclipse.lsp4j.SemanticTokens
import org.eclipse.lsp4j.SemanticTokensLegend
import org.eclipse.lsp4j.SemanticTokensWithRegistrationOptions
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds

@TestApplication
internal class LspSemanticTokensTest {
  companion object {
    private val tempDirFixture = tempPathFixture()
    private val projectFixture = projectFixture(tempDirFixture, openAfterCreation = true)
    private val project by projectFixture

    @Suppress("unused")
    private val moduleFixture = projectFixture.moduleFixture(tempDirFixture, addPathToSourceRoot = true)

    private const val KEYWORD = 0
    private const val PROPERTY = 1
    private const val VARIABLE = 2

    private const val STATIC_MODIFIER = 1 shl 1

    private val LEGEND = SemanticTokensLegend(listOf("keyword", "property", "variable"), listOf("declaration", "static"))

    private val KEYWORD_KEY = DefaultLanguageHighlighterColors.KEYWORD.externalName
  }

  private val codeInsightFixture by codeInsightFixture(projectFixture, tempDirFixture)

  private fun semanticTokensCapability(): SemanticTokensWithRegistrationOptions = SemanticTokensWithRegistrationOptions().apply {
    full = Either.forLeft(true)
    legend = LEGEND
  }

  private class OpenedFile(val document: Document, val serverSession: FakeLspServerSession, val uri: String)

  private suspend fun openFile(integration: FakeLspIntegration, fileName: String, text: String): OpenedFile {
    val virtualFile = codeInsightFixture.configureByText(fileName, text).virtualFile
    val serverSession = integration.serverSession(virtualFile)
    return OpenedFile(codeInsightFixture.editor.document, serverSession, serverSession.fileUri(virtualFile))
  }

  private suspend fun openSpaceTokenizedFile(integration: FakeLspIntegration, text: String): OpenedFile {
    val virtualFile = codeInsightFixture.configureByText(SpaceTokenizingFileType, text).virtualFile
    val serverSession = integration.serverSession(virtualFile)
    return OpenedFile(codeInsightFixture.editor.document, serverSession, serverSession.fileUri(virtualFile))
  }

  private fun OpenedFile.expectTokens(data: List<Int>): Deferred<Unit> =
    serverSession.expectRequest(serverSession.SEMANTIC_TOKENS_FULL, { it.textDocument.uri == uri }) { SemanticTokens(data) }

  /**
   * Runs the highlighting passes until the editor shows the semantic tokens in [expected]: the file text with
   * `<text_attr textAttributesKey="...">` around each token. The first pass sends the request, and the tokens arrive later.
   */
  private suspend fun checkTokenHighlighting(expected: String) {
    val data = withContext(Dispatchers.EDT) {
      ExpectedHighlightingData(DocumentImpl(expected), false, false, true).apply { init() }
    }
    waitUntilAssertSucceeds {
      (codeInsightFixture as CodeInsightTestFixtureImpl).collectAndCheckHighlighting(data)
    }
  }

  /** Runs the highlighting passes once, waits a bit and checks that [request] didn't arrive. The test still has to cancel it. */
  private suspend fun assertHighlightingSendsNoRequest(request: Deferred<Unit>) {
    codeInsightFixture.doHighlighting()
    delay(300.milliseconds)
    assertFalse(request.isCompleted, "No semanticTokens/full request was expected")
  }

  @Nested
  inner class DefaultCustomizer {
    @Suppress("unused")
    private val spaceTokenizingLang = spaceTokenizingLanguageFixture()

    private val fakeLspIntegration by projectFixture.fakeLspIntegrationFixture(
      configureServerCapabilities = {
        semanticTokensProvider = semanticTokensCapability()
      },
    )

    @Test
    fun `tokens are decoded with the legend, and modifiers select the attributes key`() = timeoutRunBlocking {
      val file = openFile(fakeLspIntegration, "decode.txt", "fun foo\n  bar baz")
      file.expectTokens(listOf(
        0, 0, 3, KEYWORD, 0,
        0, 4, 3, VARIABLE, 0,
        1, 2, 3, PROPERTY, STATIC_MODIFIER,
        0, 4, 3, PROPERTY, 0,
      ))

      checkTokenHighlighting(
        """<text_attr textAttributesKey="$KEYWORD_KEY">fun</text_attr> """ +
        """<text_attr textAttributesKey="${DefaultLanguageHighlighterColors.LOCAL_VARIABLE.externalName}">foo</text_attr>""" +
        "\n  " +
        """<text_attr textAttributesKey="${DefaultLanguageHighlighterColors.STATIC_FIELD.externalName}">bar</text_attr> """ +
        """<text_attr textAttributesKey="${DefaultLanguageHighlighterColors.INSTANCE_FIELD.externalName}">baz</text_attr>"""
      )

      file.serverSession.awaitExpected()
    }

    @Test
    fun `a token type outside the legend is skipped, and the next token is still placed by its delta`() = timeoutRunBlocking {
      val file = openFile(fakeLspIntegration, "unknownType.txt", "aaa bbb ccc")
      file.expectTokens(listOf(
        0, 0, 3, KEYWORD, 0,
        0, 4, 3, 99, 0,
        0, 4, 3, KEYWORD, 0,
      ))

      checkTokenHighlighting(
        """<text_attr textAttributesKey="$KEYWORD_KEY">aaa</text_attr> bbb <text_attr textAttributesKey="$KEYWORD_KEY">ccc</text_attr>"""
      )

      file.serverSession.awaitExpected()
    }

    @Test
    fun `a token running past the end of the document is skipped, and the earlier tokens stay`() = timeoutRunBlocking {
      val file = openFile(fakeLspIntegration, "pastEnd.txt", "aaa bbb")
      file.expectTokens(listOf(
        0, 0, 3, KEYWORD, 0,
        0, 4, 10, KEYWORD, 0,
      ))

      checkTokenHighlighting("""<text_attr textAttributesKey="$KEYWORD_KEY">aaa</text_attr> bbb""")

      file.serverSession.awaitExpected()
    }

    @Test
    fun `empty data after an edit clears the highlighters`() = timeoutRunBlocking {
      val file = openFile(fakeLspIntegration, "clear.txt", "aaa bbb")
      file.expectTokens(listOf(0, 0, 3, KEYWORD, 0))
      checkTokenHighlighting("""<text_attr textAttributesKey="$KEYWORD_KEY">aaa</text_attr> bbb""")
      file.serverSession.awaitExpected()
      file.expectTokens(emptyList())

      writeCommandAction(project, "") {
        file.document.insertString(file.document.textLength, " ccc")
      }

      checkTokenHighlighting("aaa bbb ccc")
      file.serverSession.awaitExpected()
    }

    @Test
    @Disabled("C6: workspace/semanticTokens/refresh clears the cache, so the tokens vanish until the new answer arrives")
    fun `refresh asks the server again and keeps the old tokens until the answer arrives`() = timeoutRunBlocking {
      val file = openFile(fakeLspIntegration, "refresh.txt", "aaa bbb")
      file.expectTokens(listOf(0, 0, 3, KEYWORD, 0))
      checkTokenHighlighting("""<text_attr textAttributesKey="$KEYWORD_KEY">aaa</text_attr> bbb""")
      file.serverSession.awaitExpected()
      val answerRefresh = CompletableDeferred<Unit>()
      val refreshRequest = file.serverSession.expectRequest(file.serverSession.SEMANTIC_TOKENS_FULL, { it.textDocument.uri == file.uri }) {
        answerRefresh.await()
        SemanticTokens(listOf(0, 4, 3, KEYWORD, 0))
      }

      file.serverSession.sendRequest(file.serverSession.SEMANTIC_TOKENS_REFRESH) { }
      refreshRequest.await()

      delay(300.milliseconds)
      val data = withContext(Dispatchers.EDT) {
        ExpectedHighlightingData(DocumentImpl("""<text_attr textAttributesKey="$KEYWORD_KEY">aaa</text_attr> bbb"""), false, false, true)
          .apply { init() }
      }
      (codeInsightFixture as CodeInsightTestFixtureImpl).collectAndCheckHighlighting(data)
      answerRefresh.complete(Unit)
      checkTokenHighlighting("""aaa <text_attr textAttributesKey="$KEYWORD_KEY">bbb</text_attr>""")
    }

    @Test
    fun `no request for a file of a language with PSI`() = timeoutRunBlocking {
      val file = openSpaceTokenizedFile(fakeLspIntegration, "aaa bbb")
      val request = file.expectTokens(listOf(0, 0, 3, KEYWORD, 0))

      try {
        assertHighlightingSendsNoRequest(request)
        checkTokenHighlighting("aaa bbb")
      }
      finally {
        request.cancel()
      }
    }
  }

  @Nested
  inner class CustomizedTokens {
    @Suppress("unused")
    private val spaceTokenizingLang = spaceTokenizingLanguageFixture()

    private val fakeLspIntegration by projectFixture.fakeLspIntegrationFixture(
      lspCustomization = object : LspCustomization() {
        override val semanticTokensCustomizer: LspSemanticTokensCustomizer = object : LspSemanticTokensSupport() {
          override val tokenTypes: List<String> = listOf("keyword", "customType")
          override val tokenModifiers: List<String> = listOf("customModifier")

          override fun shouldAskServerForSemanticTokens(psiFile: PsiFile): Boolean = true

          override fun getTextAttributesKey(tokenType: String, modifiers: List<String>): TextAttributesKey? = when (tokenType) {
            "keyword" -> DefaultLanguageHighlighterColors.CONSTANT
            else -> null
          }
        }
      },
      configureServerCapabilities = {
        semanticTokensProvider = semanticTokensCapability()
      },
    )

    private val constantKey = DefaultLanguageHighlighterColors.CONSTANT.externalName

    @Test
    fun `the customizer decides the attributes key, and a null key gives no highlighter`() = timeoutRunBlocking {
      val file = openFile(fakeLspIntegration, "custom.txt", "aaa bbb")
      file.expectTokens(listOf(
        0, 0, 3, KEYWORD, 0,
        0, 4, 3, VARIABLE, 0,
      ))

      checkTokenHighlighting("""<text_attr textAttributesKey="$constantKey">aaa</text_attr> bbb""")

      file.serverSession.awaitExpected()
    }

    @Test
    fun `shouldAskServerForSemanticTokens override asks the server for a file of a language with PSI`() = timeoutRunBlocking {
      val file = openSpaceTokenizedFile(fakeLspIntegration, "aaa bbb")
      file.expectTokens(listOf(0, 4, 3, KEYWORD, 0))

      checkTokenHighlighting("""aaa <text_attr textAttributesKey="$constantKey">bbb</text_attr>""")

      file.serverSession.awaitExpected()
    }

    @Test
    fun `custom token types and modifiers are advertised in the client capabilities`() = timeoutRunBlocking {
      val virtualFile = codeInsightFixture.configureByText("capabilities.txt", "aaa").virtualFile
      fakeLspIntegration.serverSession(virtualFile)

      val semanticTokens = fakeLspIntegration.client.descriptor.clientCapabilities.textDocument.semanticTokens

      assertEquals(listOf("keyword", "customType"), semanticTokens.tokenTypes)
      assertEquals(listOf("customModifier"), semanticTokens.tokenModifiers)
    }
  }

  @Nested
  inner class SemanticTokensDisabledByCustomizer {
    private val fakeLspIntegration by projectFixture.fakeLspIntegrationFixture(
      lspCustomization = object : LspCustomization() {
        override val semanticTokensCustomizer: LspSemanticTokensCustomizer = LspSemanticTokensDisabled
      },
      configureServerCapabilities = {
        semanticTokensProvider = semanticTokensCapability()
      },
    )

    @Test
    fun `no client capability and no request when the customizer disables the feature`() = timeoutRunBlocking {
      val file = openFile(fakeLspIntegration, "disabled.txt", "aaa bbb")
      val request = file.expectTokens(listOf(0, 0, 3, KEYWORD, 0))

      try {
        assertNull(fakeLspIntegration.client.descriptor.clientCapabilities.textDocument.semanticTokens)
        assertHighlightingSendsNoRequest(request)
      }
      finally {
        request.cancel()
      }
    }
  }

  @Nested
  inner class FullRequestNotSupportedByServer {
    private val fakeLspIntegration by projectFixture.fakeLspIntegrationFixture(
      configureServerCapabilities = {
        semanticTokensProvider = semanticTokensCapability().apply {
          full = Either.forLeft(false)
          range = Either.forLeft(true)
        }
      },
    )

    @Test
    fun `no request when the server supports only range requests`() = timeoutRunBlocking {
      val file = openFile(fakeLspIntegration, "rangeOnly.txt", "aaa bbb")
      val request = file.expectTokens(listOf(0, 0, 3, KEYWORD, 0))

      try {
        assertHighlightingSendsNoRequest(request)
        checkTokenHighlighting("aaa bbb")
      }
      finally {
        request.cancel()
      }
    }
  }
}
