// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.integrationTests.fakeScenario

import com.intellij.driver.sdk.ui.components.common.codeEditorForFile
import com.intellij.driver.sdk.ui.should
import com.intellij.platform.lsp.integrationTests.fakeServer.FakeLspIntegration
import com.intellij.platform.lsp.integrationTests.util.SharedLspIde
import org.eclipse.lsp4j.DocumentLink
import org.eclipse.lsp4j.DocumentLinkOptions
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.ServerCapabilities
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Cases of `LspDocumentLinkTest` of `intellij.platform.lsp.impl.tests`, in a running IDE: the test reads the highlighter of the link,
 * and follows the link with a click.
 */
@ExtendWith(SharedLspIde::class)
internal class LspFakeScenarioDocumentLinkTest {
  @Test
  fun `the range of a link is rendered as a link`(fakeLsp: FakeLspIntegration) = fakeLsp.scenario(DOCUMENT_LINKS) {
    val serverSession = unstartedServerSession()
    val file = addFile("test.txt", TEXT)

    serverSession.expectRequest(serverSession.DOCUMENT_LINK, { it.textDocument.uri.endsWith("/$file") }) {
      listOf(DocumentLink(LINK_RANGE, "https://example.com/page"))
    }
    val editor = serverSession.openFile(file)

    editor.waitForTextAttributes("""see <text_attr textAttributesKey="$LINK_KEY">LINK</text_attr> for details""")
  }

  @Test
  fun `a click with the modifier key on a link to a file opens the file`(fakeLsp: FakeLspIntegration) = fakeLsp.scenario(DOCUMENT_LINKS) {
    val serverSession = unstartedServerSession()
    val file = addFile("test.txt", TEXT)
    val targetFile = addFile("target.txt", "target file contents")

    serverSession.expectRequest(serverSession.DOCUMENT_LINK, { it.textDocument.uri.endsWith("/$file") }) {
      listOf(DocumentLink(LINK_RANGE, serverSession.fileUri(targetFile)))
    }
    val editor = serverSession.openFile(file)
    editor.waitForTextAttributes("""see <text_attr textAttributesKey="$LINK_KEY">LINK</text_attr> for details""")

    // The client asks for the links of the target file once it opens.
    serverSession.expectRequest(serverSession.DOCUMENT_LINK, { it.textDocument.uri.endsWith("/$targetFile") }) {
      emptyList()
    }
    editor.keyboard { pressing(defaultModifierKey) { editor.clickOnPosition(1, OFFSET_IN_LINK + 1) } }

    frame.codeEditorForFile("target.txt").should("the target file is open") { text == "target file contents" }
  }
}

private val DOCUMENT_LINKS: ServerCapabilities.() -> Unit = { documentLinkProvider = DocumentLinkOptions(false) }

/** `LINK` is the link. Nothing in the text looks like a path or a URL, so no reference of the IDE competes with the one of the server. */
private const val TEXT = "see LINK for details"
private val LINK_RANGE = Range(Position(0, 4), Position(0, 8))
private const val OFFSET_IN_LINK = 6

/** The external name of `CodeInsightColors.INACTIVE_HYPERLINK_ATTRIBUTES`. */
private const val LINK_KEY = "INACTIVE_HYPERLINK_ATTRIBUTES"
