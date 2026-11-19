// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.integrationTests.fakeScenario

import com.intellij.driver.sdk.ui.components.common.JEditorUiComponent
import com.intellij.platform.lsp.integrationTests.fakeServer.FakeLspIntegration
import com.intellij.platform.lsp.integrationTests.fakeServer.FakeLspScenario
import com.intellij.platform.lsp.integrationTests.fakeServer.FakeLspServerSession
import com.intellij.platform.lsp.integrationTests.fakeServer.UnansweredRequest
import com.intellij.platform.lsp.integrationTests.util.ClientCustomization
import com.intellij.platform.lsp.integrationTests.util.SharedLspIde
import com.intellij.platform.lsp.integrationTests.util.typeAt
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.SemanticTokens
import org.eclipse.lsp4j.SemanticTokensLegend
import org.eclipse.lsp4j.SemanticTokensWithRegistrationOptions
import org.eclipse.lsp4j.ServerCapabilities
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Cases of `LspSemanticTokensTest` and `LspHighlightingApplierTest` of `intellij.platform.lsp.impl.tests`, in a running IDE:
 * the test reads the text attributes keys of the highlighters of the editor.
 */
@ExtendWith(SharedLspIde::class)
internal class LspFakeScenarioSemanticTokensTest {
  @Test
  fun `tokens are decoded with the legend, and modifiers select the attributes key`(fakeLsp: FakeLspIntegration) = fakeLsp.scenario(SEMANTIC_TOKENS) {
    val (_, editor) = openFileWithTokens("decode.txt", "fun foo\n  bar baz", listOf(
      0, 0, 3, KEYWORD, 0,
      0, 4, 3, VARIABLE, 0,
      1, 2, 3, PROPERTY, STATIC_MODIFIER,
      0, 4, 3, PROPERTY, 0,
    ))

    editor.waitForTextAttributes(
      """<text_attr textAttributesKey="$KEYWORD_KEY">fun</text_attr> """ +
      """<text_attr textAttributesKey="DEFAULT_LOCAL_VARIABLE">foo</text_attr>""" +
      "\n  " +
      """<text_attr textAttributesKey="DEFAULT_STATIC_FIELD">bar</text_attr> """ +
      """<text_attr textAttributesKey="DEFAULT_INSTANCE_FIELD">baz</text_attr>"""
    )
  }

  @Test
  fun `a token type outside the legend is skipped, and the next token is still placed by its delta`(fakeLsp: FakeLspIntegration) = fakeLsp.scenario(SEMANTIC_TOKENS) {
    val (_, editor) = openFileWithTokens("unknownType.txt", "aaa bbb ccc", listOf(
      0, 0, 3, KEYWORD, 0,
      0, 4, 3, 99, 0,
      0, 4, 3, KEYWORD, 0,
    ))

    editor.waitForTextAttributes(
      """<text_attr textAttributesKey="$KEYWORD_KEY">aaa</text_attr> bbb <text_attr textAttributesKey="$KEYWORD_KEY">ccc</text_attr>"""
    )
  }

  @Test
  fun `a token running past the end of the document is skipped, and the earlier tokens stay`(fakeLsp: FakeLspIntegration) = fakeLsp.scenario(SEMANTIC_TOKENS) {
    val (_, editor) = openFileWithTokens("pastEnd.txt", "aaa bbb", listOf(
      0, 0, 3, KEYWORD, 0,
      0, 4, 10, KEYWORD, 0,
    ))

    editor.waitForTextAttributes("""<text_attr textAttributesKey="$KEYWORD_KEY">aaa</text_attr> bbb""")
  }

  @Test
  fun `empty data after an edit clears the highlighters`(fakeLsp: FakeLspIntegration) = fakeLsp.scenario(SEMANTIC_TOKENS) {
    val (serverSession, editor) = openFileWithTokens("clear.txt", "aaa bbb", listOf(0, 0, 3, KEYWORD, 0))
    editor.waitForTextAttributes("""<text_attr textAttributesKey="$KEYWORD_KEY">aaa</text_attr> bbb""")

    serverSession.expectRequest(serverSession.SEMANTIC_TOKENS_FULL, { it.textDocument.uri.endsWith("/clear.txt") }) {
      SemanticTokens(emptyList())
    }
    editor.typeAt(editor.text.length, "c")

    editor.waitForTextAttributes("aaa bbbc", absentKeys = setOf(KEYWORD_KEY))
  }

  @Test
  fun `local edit re-applies semantic token highlighters at adjusted offsets`(fakeLsp: FakeLspIntegration) = fakeLsp.scenario(SEMANTIC_TOKENS) {
    val (serverSession, editor) = openFileWithTokens("adjust.txt", "hello world", listOf(0, 0, 5, KEYWORD, 0))
    editor.waitForTextAttributes("""<text_attr textAttributesKey="$KEYWORD_KEY">hello</text_attr> world""")

    // The server never answers the request that the edit causes, so the editor can only show the old token, moved by the edit.
    val pendingResponse = UnansweredRequest()
    serverSession.expectRequest(serverSession.SEMANTIC_TOKENS_FULL, { it.textDocument.uri.endsWith("/adjust.txt") }) {
      pendingResponse.hold()
    }
    editor.typeAt(0, "x")

    editor.waitForTextAttributes("""x<text_attr textAttributesKey="$KEYWORD_KEY">hello</text_attr> world""")
  }

  @Test
  fun `no client capability and no request when the customizer disables the feature`(fakeLsp: FakeLspIntegration) = fakeLsp.scenario(
    SEMANTIC_TOKENS, clientCustomization = ClientCustomization.SEMANTIC_TOKENS_DISABLED,
  ) {
    val serverSession = unstartedServerSession()
    val file = addFile("disabled.txt", "aaa bbb")

    // No expectation of `semanticTokens/full`: such a request would fail the test as an unexpected message.
    val editor = serverSession.openFile(file)

    assertThat(serverSession.clientCapabilities.textDocument.semanticTokens).isNull()
    editor.waitForTextAttributes("aaa bbb", absentKeys = setOf(KEYWORD_KEY))
  }
}

/**
 * Creates the file, expects the `semanticTokens/full` request that the client sends right after `didOpen`,
 * then opens the file and waits until the server got `didOpen`.
 */
private fun FakeLspScenario.openFileWithTokens(name: String, text: String, firstTokens: List<Int>): Pair<FakeLspServerSession, JEditorUiComponent> {
  val serverSession = unstartedServerSession()
  val file = addFile(name, text)
  serverSession.expectRequest(serverSession.SEMANTIC_TOKENS_FULL, { it.textDocument.uri.endsWith("/$file") }) {
    SemanticTokens(firstTokens)
  }
  return serverSession to serverSession.openFile(file)
}

private const val KEYWORD = 0
private const val PROPERTY = 1
private const val VARIABLE = 2

private const val STATIC_MODIFIER = 1 shl 1

private val SEMANTIC_TOKENS: ServerCapabilities.() -> Unit = {
  semanticTokensProvider = SemanticTokensWithRegistrationOptions().apply {
    full = Either.forLeft(true)
    legend = SemanticTokensLegend(listOf("keyword", "property", "variable"), listOf("declaration", "static"))
  }
}

/** The external name of `DefaultLanguageHighlighterColors.KEYWORD`. */
private const val KEYWORD_KEY = "DEFAULT_KEYWORD"
