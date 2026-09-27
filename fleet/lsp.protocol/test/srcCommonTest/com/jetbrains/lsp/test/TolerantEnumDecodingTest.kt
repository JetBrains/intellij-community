package com.jetbrains.lsp.test

import com.jetbrains.lsp.protocol.ClientWorkspaceCapabilities
import com.jetbrains.lsp.protocol.CodeActionTriggerKind
import com.jetbrains.lsp.protocol.CompletionItem
import com.jetbrains.lsp.protocol.CompletionItemKind
import com.jetbrains.lsp.protocol.CompletionItemTag
import com.jetbrains.lsp.protocol.CompletionTriggerKind
import com.jetbrains.lsp.protocol.DiagnosticSeverity
import com.jetbrains.lsp.protocol.DiagnosticTag
import com.jetbrains.lsp.protocol.DocumentHighlightKind
import com.jetbrains.lsp.protocol.DocumentSymbol
import com.jetbrains.lsp.protocol.DocumentSymbolClientCapabilities
import com.jetbrains.lsp.protocol.FileChangeType
import com.jetbrains.lsp.protocol.InlayHintKind
import com.jetbrains.lsp.protocol.InsertTextFormat
import com.jetbrains.lsp.protocol.InsertTextMode
import com.jetbrains.lsp.protocol.LSP
import com.jetbrains.lsp.protocol.MessageType
import com.jetbrains.lsp.protocol.NotebookCellKind
import com.jetbrains.lsp.protocol.PrepareSupportDefaultBehavior
import com.jetbrains.lsp.protocol.PublishDiagnosticsParams
import com.jetbrains.lsp.protocol.ShowMessageParams
import com.jetbrains.lsp.protocol.SignatureHelpTriggerKind
import com.jetbrains.lsp.protocol.SymbolKind
import com.jetbrains.lsp.protocol.SymbolTag
import com.jetbrains.lsp.protocol.TextDocumentSaveReason
import com.jetbrains.lsp.protocol.TextDocumentSyncKind
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonPrimitive
import kotlin.enums.enumEntries
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * An int enum value the protocol does not know must not fail the whole message (C4).
 * A scalar decodes to the enum's fallback entry; a tag or value-set list drops the unknown codes.
 */
class TolerantEnumDecodingTest {

  private class Case<T : Enum<T>>(
    val serializer: KSerializer<T>,
    val entries: List<T>,
    val code: (T) -> Int,
    val fallback: T,
  ) {
    fun check() {
      for (entry in entries) {
        val encoded = LSP.json.encodeToJsonElement(serializer, entry)
        assertEquals(JsonPrimitive(code(entry)), encoded, "encode $entry")
        assertEquals(entry, LSP.json.decodeFromJsonElement(serializer, encoded), "decode $entry")
        assertEquals(entry, LSP.json.decodeFromString(serializer, "${code(entry)}"), "streaming decode $entry")
      }
      val known = entries.map(code).toSet()
      for (unknown in listOf(-1, 0, 99, 1000).filter { it !in known }) {
        assertEquals(fallback, LSP.json.decodeFromJsonElement(serializer, JsonPrimitive(unknown)), "fallback of ${serializer.descriptor.serialName} for $unknown")
        assertEquals(fallback, LSP.json.decodeFromString(serializer, "$unknown"), "streaming fallback of ${serializer.descriptor.serialName} for $unknown")
      }
    }
  }

  private inline fun <reified T : Enum<T>> case(serializer: KSerializer<T>, fallback: T, noinline code: (T) -> Int): Case<T> =
    Case(serializer, enumEntries<T>(), code, fallback)

  @Test
  fun `every int enum round-trips known values and decodes unknown values to its fallback`() {
    listOf(
      case(SymbolKind.serializer(), SymbolKind.Property) { it.value },
      case(SymbolTag.serializer(), SymbolTag.Deprecated) { it.value },
      case(NotebookCellKind.serializer(), NotebookCellKind.Code) { it.value },
      case(DocumentHighlightKind.serializer(), DocumentHighlightKind.Text) { it.value },
      case(CompletionTriggerKind.serializer(), CompletionTriggerKind.Invoked) { it.value },
      case(InsertTextFormat.serializer(), InsertTextFormat.PlainText) { it.value },
      case(CompletionItemTag.serializer(), CompletionItemTag.Deprecated) { it.value },
      case(InsertTextMode.serializer(), InsertTextMode.AsIs) { it.value },
      case(CompletionItemKind.serializer(), CompletionItemKind.Text) { it.kind },
      case(DiagnosticSeverity.serializer(), DiagnosticSeverity.Error) { it.value },
      case(DiagnosticTag.serializer(), DiagnosticTag.Unnecessary) { it.value },
      case(MessageType.serializer(), MessageType.Log) { it.value },
      case(FileChangeType.serializer(), FileChangeType.Changed) { it.value },
      case(InlayHintKind.serializer(), InlayHintKind.Type) { it.value },
      case(SignatureHelpTriggerKind.serializer(), SignatureHelpTriggerKind.Invoked) { it.value },
      case(CodeActionTriggerKind.serializer(), CodeActionTriggerKind.Invoked) { it.value },
      case(PrepareSupportDefaultBehavior.serializer(), PrepareSupportDefaultBehavior.Identifier) { it.value },
      case(TextDocumentSyncKind.serializer(), TextDocumentSyncKind.Full) { it.value },
      case(TextDocumentSaveReason.serializer(), TextDocumentSaveReason.Manual) { it.code },
    ).forEach { it.check() }
  }

  @Test
  fun `publishDiagnostics with unknown severity and tags decodes`() {
    val params = LSP.json.decodeFromString(
      PublishDiagnosticsParams.serializer(),
      """{"uri":"file:///a.kt","diagnostics":[
        {"range":{"start":{"line":0,"character":0},"end":{"line":0,"character":1}},"message":"m","severity":7,"tags":[2,42,1]}
      ]}""",
    )
    val diagnostic = params.diagnostics.single()
    assertEquals(DiagnosticSeverity.Error, diagnostic.severity)
    assertEquals(listOf(DiagnosticTag.Deprecated, DiagnosticTag.Unnecessary), diagnostic.tags)
  }

  @Test
  fun `completion item with unknown kind, tags and formats decodes`() {
    val item = LSP.json.decodeFromString(
      CompletionItem.serializer(),
      """{"label":"foo","kind":250,"tags":[9,1],"insertTextFormat":5,"insertTextMode":0}""",
    )
    assertEquals(CompletionItemKind.Text, item.kind)
    assertEquals(listOf(CompletionItemTag.Deprecated), item.tags)
    assertEquals(InsertTextFormat.PlainText, item.insertTextFormat)
    assertEquals(InsertTextMode.AsIs, item.insertTextMode)
  }

  @Test
  fun `document symbol with unknown kind and tags decodes`() {
    val symbol = LSP.json.decodeFromString(
      DocumentSymbol.serializer(),
      """{"name":"s","kind":0,"tags":[3],
        "range":{"start":{"line":0,"character":0},"end":{"line":1,"character":0}},
        "selectionRange":{"start":{"line":0,"character":0},"end":{"line":0,"character":1}}}""",
    )
    assertEquals(SymbolKind.Property, symbol.kind)
    assertEquals(emptyList(), symbol.tags)
  }

  @Test
  fun `show message with unknown type decodes`() {
    val params = LSP.json.decodeFromString(ShowMessageParams.serializer(), """{"type":17,"message":"hi"}""")
    assertEquals(MessageType.Log, params.type)
  }

  @Test
  fun `client value sets with unknown codes decode`() {
    val capabilities = LSP.json.decodeFromString(
      ClientWorkspaceCapabilities.serializer(),
      """{"symbol":{"symbolKind":{"valueSet":[1,77]},"tagSupport":{"valueSet":[1,5]}}}""",
    )
    val symbol = capabilities.symbol!!
    assertEquals(listOf(SymbolKind.File), symbol.symbolKind?.valueSet)
    assertEquals(listOf(SymbolTag.Deprecated), symbol.tagSupport?.valueSet)
    assertNull(capabilities.applyEdit)
  }

  @Test
  fun `document symbol value sets drop unknown codes`() {
    val capabilities = LSP.json.decodeFromString(
      DocumentSymbolClientCapabilities.serializer(),
      """{"symbolKind":{"valueSet":[2,77]},"tagSupport":{"valueSet":[5]}}""",
    )
    assertEquals(listOf(SymbolKind.Module), capabilities.symbolKind?.valueSet)
    assertEquals(emptyList(), capabilities.tagSupport?.valueSet)
  }
}
