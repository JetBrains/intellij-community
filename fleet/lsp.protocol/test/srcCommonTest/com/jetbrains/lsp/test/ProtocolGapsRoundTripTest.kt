package com.jetbrains.lsp.test

import com.jetbrains.lsp.protocol.ChangeAnnotationIdentifier
import com.jetbrains.lsp.protocol.CreateFilesParams
import com.jetbrains.lsp.protocol.DeleteFilesParams
import com.jetbrains.lsp.protocol.DocumentLink
import com.jetbrains.lsp.protocol.DocumentLinkParams
import com.jetbrains.lsp.protocol.DocumentLinks
import com.jetbrains.lsp.protocol.DocumentOnTypeFormattingParams
import com.jetbrains.lsp.protocol.DocumentRangesFormattingParams
import com.jetbrains.lsp.protocol.DocumentSync
import com.jetbrains.lsp.protocol.DocumentUri
import com.jetbrains.lsp.protocol.FileCreate
import com.jetbrains.lsp.protocol.FileDelete
import com.jetbrains.lsp.protocol.FileRename
import com.jetbrains.lsp.protocol.FormattingOptions
import com.jetbrains.lsp.protocol.LSP
import com.jetbrains.lsp.protocol.NoValueSerializer
import com.jetbrains.lsp.protocol.OnTypeFormattingRequestType
import com.jetbrains.lsp.protocol.Position
import com.jetbrains.lsp.protocol.Range
import com.jetbrains.lsp.protocol.RangesFormattingRequestType
import com.jetbrains.lsp.protocol.RenameFilesParams
import com.jetbrains.lsp.protocol.TextDocumentContentRefreshParams
import com.jetbrains.lsp.protocol.TextDocumentEdit
import com.jetbrains.lsp.protocol.TextDocumentIdentifier
import com.jetbrains.lsp.protocol.TextDocumentSaveReason
import com.jetbrains.lsp.protocol.URI
import com.jetbrains.lsp.protocol.WillSaveTextDocumentParams
import com.jetbrains.lsp.protocol.Workspace
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * Round-trips the protocol types added for the Draft LSP client, and pins their method names.
 */
class ProtocolGapsRoundTripTest {

  private val uri = URI("file:///project/a.kt")
  private val document = TextDocumentIdentifier(DocumentUri(uri))
  private val range = Range(Position(1, 2), Position(3, 4))

  private fun <T> assertRoundTrip(serializer: KSerializer<T>, value: T, expectedJson: String) {
    val encoded = LSP.json.encodeToJsonElement(serializer, value)
    assertEquals(Json.parseToJsonElement(expectedJson), encoded)
    assertEquals(value, LSP.json.decodeFromJsonElement(serializer, encoded))
    assertEquals(value, LSP.json.decodeFromString(serializer, expectedJson))
  }

  @Test
  fun `method names follow the spec`() {
    val methods = mapOf(
      Workspace.WorkspaceFolders.method to "workspace/workspaceFolders",
      OnTypeFormattingRequestType.method to "textDocument/onTypeFormatting",
      RangesFormattingRequestType.method to "textDocument/rangesFormatting",
      DocumentSync.WillSave.method to "textDocument/willSave",
      Workspace.RefreshTextDocumentContent.method to "workspace/textDocumentContent/refresh",
      Workspace.RefreshFoldingRanges.method to "workspace/foldingRange/refresh",
      Workspace.WillCreateFiles.method to "workspace/willCreateFiles",
      Workspace.DidCreateFiles.method to "workspace/didCreateFiles",
      Workspace.DidRenameFiles.method to "workspace/didRenameFiles",
      Workspace.WillDeleteFiles.method to "workspace/willDeleteFiles",
      Workspace.DidDeleteFiles.method to "workspace/didDeleteFiles",
      DocumentLinks.DocumentLinkRequestType.method to "textDocument/documentLink",
      DocumentLinks.ResolveDocumentLink.method to "documentLink/resolve",
    )
    for ((actual, expected) in methods) {
      assertEquals(expected, actual)
    }
  }

  @Test
  fun `refresh requests carry no values`() {
    val (_, params, result, error) = Workspace.RefreshFoldingRanges
    assertSame(NoValueSerializer, params)
    assertSame(NoValueSerializer, result)
    assertSame(NoValueSerializer, error)
    assertSame(NoValueSerializer, Workspace.RefreshTextDocumentContent.resultSerializer)
    assertRoundTrip(
      TextDocumentContentRefreshParams.serializer(),
      TextDocumentContentRefreshParams(DocumentUri(URI("jar:///lib.jar!/A.class"))),
      """{"uri":"jar:///lib.jar!/A.class"}""",
    )
  }

  @Test
  fun `on type formatting params round-trip`() {
    assertRoundTrip(
      DocumentOnTypeFormattingParams.serializer(),
      DocumentOnTypeFormattingParams(document, Position(5, 6), "}", FormattingOptions(tabSize = 4, insertSpaces = true)),
      """{"textDocument":{"uri":"file:///project/a.kt"},"position":{"line":5,"character":6},"ch":"}",
        "options":{"tabSize":4,"insertSpaces":true}}""",
    )
  }

  @Test
  fun `ranges formatting params round-trip`() {
    assertRoundTrip(
      DocumentRangesFormattingParams.serializer(),
      DocumentRangesFormattingParams(document, listOf(range), FormattingOptions(tabSize = 2, insertSpaces = false)),
      """{"textDocument":{"uri":"file:///project/a.kt"},
        "ranges":[{"start":{"line":1,"character":2},"end":{"line":3,"character":4}}],
        "options":{"tabSize":2,"insertSpaces":false}}""",
    )
  }

  @Test
  fun `will save params round-trip`() {
    assertRoundTrip(
      DocumentSync.WillSave.paramsSerializer,
      WillSaveTextDocumentParams(document, TextDocumentSaveReason.AfterDelay),
      """{"textDocument":{"uri":"file:///project/a.kt"},"reason":2}""",
    )
  }

  @Test
  fun `file operation params round-trip`() {
    assertRoundTrip(
      CreateFilesParams.serializer(),
      CreateFilesParams(listOf(FileCreate(uri))),
      """{"files":[{"uri":"file:///project/a.kt"}]}""",
    )
    assertRoundTrip(
      DeleteFilesParams.serializer(),
      DeleteFilesParams(listOf(FileDelete(uri))),
      """{"files":[{"uri":"file:///project/a.kt"}]}""",
    )
    assertRoundTrip(
      Workspace.DidRenameFiles.paramsSerializer,
      RenameFilesParams(listOf(FileRename(uri, URI("file:///project/b.kt")))),
      """{"files":[{"oldUri":"file:///project/a.kt","newUri":"file:///project/b.kt"}]}""",
    )
  }

  @Test
  fun `document link round-trips and keeps any target string`() {
    assertRoundTrip(
      DocumentLinkParams.serializer(),
      DocumentLinkParams(document),
      """{"textDocument":{"uri":"file:///project/a.kt"}}""",
    )
    assertRoundTrip(
      DocumentLink.serializer(),
      DocumentLink(range, target = "https://example.com/a b?q=1", tooltip = "open", data = JsonPrimitive(7)),
      """{"range":{"start":{"line":1,"character":2},"end":{"line":3,"character":4}},
        "target":"https://example.com/a b?q=1","tooltip":"open","data":7}""",
    )
    val unresolved = LSP.json.decodeFromString(
      DocumentLinks.DocumentLinkRequestType.resultSerializer,
      """[{"range":{"start":{"line":1,"character":2},"end":{"line":3,"character":4}},"data":{"id":1}}]""",
    )!!.single()
    assertNull(unresolved.target)
    assertEquals(Json.parseToJsonElement("""{"id":1}"""), unresolved.data as JsonElement)
  }

  @Test
  fun `text document edit accepts snippet and annotated edits`() {
    val edit = LSP.json.decodeFromString(
      TextDocumentEdit.serializer(),
      """{"textDocument":{"uri":"file:///project/a.kt","version":3},"edits":[
        {"range":{"start":{"line":1,"character":2},"end":{"line":3,"character":4}},"newText":"plain"},
        {"range":{"start":{"line":1,"character":2},"end":{"line":3,"character":4}},"newText":"x","annotationId":"a1"},
        {"range":{"start":{"line":1,"character":2},"end":{"line":3,"character":4}},"snippet":{"kind":"snippet","value":"${'$'}{1:x}"}}
      ]}""",
    )
    assertEquals(3, edit.textDocument.version)
    val (plain, annotated, snippet) = edit.edits
    assertEquals("plain", plain.newText)
    assertEquals(ChangeAnnotationIdentifier("a1"), annotated.annotationId)
    assertEquals("${'$'}{1:x}", snippet.snippet)
    assertEquals(edit, LSP.json.decodeFromJsonElement(TextDocumentEdit.serializer(), LSP.json.encodeToJsonElement(TextDocumentEdit.serializer(), edit)))
  }
}
