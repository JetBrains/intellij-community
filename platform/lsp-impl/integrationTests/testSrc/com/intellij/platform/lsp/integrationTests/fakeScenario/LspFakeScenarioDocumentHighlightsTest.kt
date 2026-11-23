// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.integrationTests.fakeScenario

import com.intellij.platform.lsp.integrationTests.fakeServer.FakeLspIntegration
import com.intellij.platform.lsp.integrationTests.util.SharedLspIde
import com.intellij.platform.lsp.integrationTests.util.putCaretAt
import org.eclipse.lsp4j.DocumentHighlight
import org.eclipse.lsp4j.DocumentHighlightKind
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.ServerCapabilities
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Cases of `LspDocumentHighlightsTest` of `intellij.platform.lsp.impl.tests`, in a running IDE: the test puts the caret on a symbol,
 * and the IDE highlights the usages on its own.
 *
 * The IDE also asks for the highlights of the place where the caret is when the file opens, and where the click lands that gives
 * the editor the focus. The tests don't care about those requests, so the fake server answers them with `null`.
 */
@ExtendWith(SharedLspIde::class)
internal class LspFakeScenarioDocumentHighlightsTest {
  @Test
  fun `all occurrences of the symbol under the caret are highlighted`(fakeLsp: FakeLspIntegration) = fakeLsp.scenario(
    DOCUMENT_HIGHLIGHTS, allowUnmatched = { DOCUMENT_SYNC + DOCUMENT_HIGHLIGHT },
  ) {
    val file = addFile("test.txt", "foo bar foo baz foo")
    val editor = openFile(file)
    val serverSession = serverSession(file)

    serverSession.expectRequest(serverSession.DOCUMENT_HIGHLIGHT, { it.textDocument.uri.endsWith("/$file") && it.position == Position(0, 9) }) {
      listOf(
        DocumentHighlight(Range(Position(0, 0), Position(0, 3)), DocumentHighlightKind.Read),
        DocumentHighlight(Range(Position(0, 8), Position(0, 11)), DocumentHighlightKind.Read),
        DocumentHighlight(Range(Position(0, 16), Position(0, 19)), DocumentHighlightKind.Read),
      )
    }
    editor.putCaretAt(9)

    editor.waitForTextAttributes(
      """<text_attr textAttributesKey="$READ_KEY">foo</text_attr> bar <text_attr textAttributesKey="$READ_KEY">foo</text_attr> baz """ +
      """<text_attr textAttributesKey="$READ_KEY">foo</text_attr>""",
      absentKeys = setOf(WRITE_KEY),
    )
  }

  @Test
  fun `read and write usages are distinguished by kind`(fakeLsp: FakeLspIntegration) = fakeLsp.scenario(
    DOCUMENT_HIGHLIGHTS, allowUnmatched = { DOCUMENT_SYNC + DOCUMENT_HIGHLIGHT },
  ) {
    val file = addFile("test.txt", "val x = 1\nprint(x)\nx = 2")
    val editor = openFile(file)
    val serverSession = serverSession(file)

    serverSession.expectRequest(serverSession.DOCUMENT_HIGHLIGHT, { it.textDocument.uri.endsWith("/$file") && it.position == Position(1, 6) }) {
      listOf(
        DocumentHighlight(Range(Position(0, 4), Position(0, 5)), DocumentHighlightKind.Write),
        DocumentHighlight(Range(Position(1, 6), Position(1, 7)), DocumentHighlightKind.Read),
        DocumentHighlight(Range(Position(2, 0), Position(2, 1)), DocumentHighlightKind.Write),
      )
    }
    editor.putCaretAt(16)

    editor.waitForTextAttributes(
      """val <text_attr textAttributesKey="$WRITE_KEY">x</text_attr> = 1
        |print(<text_attr textAttributesKey="$READ_KEY">x</text_attr>)
        |<text_attr textAttributesKey="$WRITE_KEY">x</text_attr> = 2""".trimMargin()
    )
  }

  @Test
  fun `usages with the text kind and with no kind are highlighted as read`(fakeLsp: FakeLspIntegration) = fakeLsp.scenario(
    DOCUMENT_HIGHLIGHTS, allowUnmatched = { DOCUMENT_SYNC + DOCUMENT_HIGHLIGHT },
  ) {
    val file = addFile("test.txt", "foo bar foo")
    val editor = openFile(file)
    val serverSession = serverSession(file)

    serverSession.expectRequest(serverSession.DOCUMENT_HIGHLIGHT, { it.textDocument.uri.endsWith("/$file") && it.position == Position(0, 9) }) {
      listOf(
        DocumentHighlight(Range(Position(0, 0), Position(0, 3)), DocumentHighlightKind.Text),
        DocumentHighlight(Range(Position(0, 8), Position(0, 11)), null),
      )
    }
    editor.putCaretAt(9)

    editor.waitForTextAttributes(
      """<text_attr textAttributesKey="$READ_KEY">foo</text_attr> bar <text_attr textAttributesKey="$READ_KEY">foo</text_attr>""",
      absentKeys = setOf(WRITE_KEY),
    )
  }
}

private val DOCUMENT_HIGHLIGHTS: ServerCapabilities.() -> Unit = { documentHighlightProvider = Either.forLeft(true) }

/** The external names of `EditorColors.IDENTIFIER_UNDER_CARET_ATTRIBUTES` and of its write variant. */
private const val READ_KEY = "IDENTIFIER_UNDER_CARET_ATTRIBUTES"
private const val WRITE_KEY = "WRITE_IDENTIFIER_UNDER_CARET_ATTRIBUTES"
