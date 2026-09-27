package com.jetbrains.lsp.test

import com.jetbrains.lsp.protocol.ClientWorkspaceCapabilities
import com.jetbrains.lsp.protocol.Command
import com.jetbrains.lsp.protocol.DocumentRangeFormattingClientCapabilities
import com.jetbrains.lsp.protocol.DocumentUri
import com.jetbrains.lsp.protocol.FoldingRangeWorkspaceClientCapabilities
import com.jetbrains.lsp.protocol.InlineCompletionClientCapabilities
import com.jetbrains.lsp.protocol.InlineCompletionContext
import com.jetbrains.lsp.protocol.InlineCompletionItem
import com.jetbrains.lsp.protocol.InlineCompletionList
import com.jetbrains.lsp.protocol.InlineCompletionParams
import com.jetbrains.lsp.protocol.InlineCompletionRegistrationOptions
import com.jetbrains.lsp.protocol.InlineCompletionRequestType
import com.jetbrains.lsp.protocol.InlineCompletionResult
import com.jetbrains.lsp.protocol.InlineCompletionTriggerKind
import com.jetbrains.lsp.protocol.LSP
import com.jetbrains.lsp.protocol.OrBoolean
import com.jetbrains.lsp.protocol.OrString
import com.jetbrains.lsp.protocol.Position
import com.jetbrains.lsp.protocol.Range
import com.jetbrains.lsp.protocol.SelectedCompletionInfo
import com.jetbrains.lsp.protocol.ServerCapabilities
import com.jetbrains.lsp.protocol.StringValue
import com.jetbrains.lsp.protocol.TextDocumentClientCapabilities
import com.jetbrains.lsp.protocol.TextDocumentIdentifier
import com.jetbrains.lsp.protocol.URI
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Round-trips the LSP 3.18 inline completion types and the client capabilities added for the Draft LSP client (S15a).
 */
class InlineCompletionRoundTripTest {

  private val document = TextDocumentIdentifier(DocumentUri(URI("file:///project/a.kt")))
  private val range = Range(Position(1, 2), Position(1, 4))
  private val rangeJson = """{"start":{"line":1,"character":2},"end":{"line":1,"character":4}}"""

  private fun <T> assertRoundTrip(serializer: KSerializer<T>, value: T, expectedJson: String) {
    val encoded = LSP.json.encodeToJsonElement(serializer, value)
    assertEquals(Json.parseToJsonElement(expectedJson), encoded)
    assertEquals(value, LSP.json.decodeFromJsonElement(serializer, encoded))
    assertEquals(value, LSP.json.decodeFromString(serializer, expectedJson))
  }

  @Test
  fun `method name follows the spec`() {
    assertEquals("textDocument/inlineCompletion", InlineCompletionRequestType.method)
  }

  @Test
  fun `params round-trip with and without selected completion info`() {
    assertRoundTrip(
      InlineCompletionRequestType.paramsSerializer,
      InlineCompletionParams(document, Position(5, 6), InlineCompletionContext(InlineCompletionTriggerKind.Automatic)),
      """{"textDocument":{"uri":"file:///project/a.kt"},"position":{"line":5,"character":6},"context":{"triggerKind":2}}""",
    )
    assertRoundTrip(
      InlineCompletionParams.serializer(),
      InlineCompletionParams(
        document, Position(1, 4),
        InlineCompletionContext(InlineCompletionTriggerKind.Invoked, SelectedCompletionInfo(range, "print")),
        workDoneToken = null,
      ),
      """{"textDocument":{"uri":"file:///project/a.kt"},"position":{"line":1,"character":4},
        "context":{"triggerKind":1,"selectedCompletionInfo":{"range":$rangeJson,"text":"print"}}}""",
    )
  }

  @Test
  fun `unknown trigger kind decodes to Invoked`() {
    val context = LSP.json.decodeFromString(InlineCompletionContext.serializer(), """{"triggerKind":9}""")
    assertEquals(InlineCompletionTriggerKind.Invoked, context.triggerKind)
  }

  @Test
  fun `item insert text is a string or a snippet`() {
    assertRoundTrip(
      InlineCompletionItem.serializer(),
      InlineCompletionItem(OrString("println()")),
      """{"insertText":"println()"}""",
    )
    assertRoundTrip(
      InlineCompletionItem.serializer(),
      InlineCompletionItem(
        insertText = OrString.of(StringValue("println(${'$'}1)${'$'}0")),
        filterText = "pri",
        range = range,
        command = Command("Accepted", "inline.accepted", listOf(JsonPrimitive(1))),
      ),
      """{"insertText":{"kind":"snippet","value":"println(${'$'}1)${'$'}0"},"filterText":"pri","range":$rangeJson,
        "command":{"title":"Accepted","command":"inline.accepted","arguments":[1]}}""",
    )
  }

  @Test
  fun `result is an item array, an item list or null`() {
    val result = InlineCompletionRequestType.resultSerializer
    val item = InlineCompletionItem(OrString("a"))
    assertRoundTrip(result, InlineCompletionResult.Items(listOf(item)), """[{"insertText":"a"}]""")
    assertRoundTrip(result, InlineCompletionResult.ItemList(InlineCompletionList(listOf(item))), """{"items":[{"insertText":"a"}]}""")
    assertEquals(listOf(item), LSP.json.decodeFromString(result, """{"items":[{"insertText":"a"}]}""")!!.items)
    assertNull(LSP.json.decodeFromString(result, "null"))
  }

  @Test
  fun `server inline completion provider is a boolean or options`() {
    assertRoundTrip(
      ServerCapabilities.serializer(),
      ServerCapabilities(inlineCompletionProvider = OrBoolean(true)),
      """{"inlineCompletionProvider":true}""",
    )
    assertRoundTrip(
      ServerCapabilities.serializer(),
      ServerCapabilities(inlineCompletionProvider = OrBoolean.of(InlineCompletionRegistrationOptions(workDoneProgress = true, id = "ic"))),
      """{"inlineCompletionProvider":{"workDoneProgress":true,"id":"ic"}}""",
    )
    assertNull(LSP.json.decodeFromString(ServerCapabilities.serializer(), "{}").inlineCompletionProvider)
  }

  @Test
  fun `text document client capabilities carry rangesSupport and inline completion`() {
    assertRoundTrip(
      TextDocumentClientCapabilities.serializer(),
      TextDocumentClientCapabilities(
        rangeFormatting = DocumentRangeFormattingClientCapabilities(dynamicRegistration = null, rangesSupport = true),
        inlineCompletion = InlineCompletionClientCapabilities(dynamicRegistration = true),
      ),
      """{"rangeFormatting":{"rangesSupport":true},"inlineCompletion":{"dynamicRegistration":true}}""",
    )
    val old = LSP.json.decodeFromString(DocumentRangeFormattingClientCapabilities.serializer(), """{"dynamicRegistration":true}""")
    assertNull(old.rangesSupport)
  }

  @Test
  fun `workspace client capabilities carry folding range refresh support`() {
    assertRoundTrip(
      ClientWorkspaceCapabilities.serializer(),
      ClientWorkspaceCapabilities(foldingRange = FoldingRangeWorkspaceClientCapabilities(refreshSupport = true)),
      """{"foldingRange":{"refreshSupport":true}}""",
    )
  }
}
