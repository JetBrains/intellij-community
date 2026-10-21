package com.jetbrains.lsp.test

import com.jetbrains.lsp.protocol.DocumentSymbol
import com.jetbrains.lsp.protocol.DocumentSymbolIntellij
import com.jetbrains.lsp.protocol.DocumentSymbolResult
import com.jetbrains.lsp.protocol.LSP
import com.jetbrains.lsp.protocol.Position
import com.jetbrains.lsp.protocol.Range
import com.jetbrains.lsp.protocol.SymbolKind
import com.jetbrains.lsp.protocol.TextDocuments
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * The JetBrains extension `DocumentSymbol.intellij`: absent from the JSON when null, so generic clients get pure spec JSON.
 */
class DocumentSymbolIntellijTest {
  private val range = Range(Position(1, 2), Position(3, 4))
  private val r = """{"start":{"line":1,"character":2},"end":{"line":3,"character":4}}"""
  private val plain = DocumentSymbol(name = "a", kind = SymbolKind.Function, range = range, selectionRange = range)
  private val plainJson = """{"name":"a","kind":12,"range":$r,"selectionRange":$r}"""
  private val marked = plain.copy(intellij = DocumentSymbolIntellij("lambda"))
  private val markedJson = """{"name":"a","kind":12,"range":$r,"selectionRange":$r,"intellij":{"origin":"lambda"}}"""

  @Test
  fun `a symbol without the extension encodes with no intellij key`() {
    assertEquals(plainJson, LSP.json.encodeToString(DocumentSymbol.serializer(), plain))
  }

  @Test
  fun `a symbol with the extension encodes the intellij key`() {
    assertEquals(markedJson, LSP.json.encodeToString(DocumentSymbol.serializer(), marked))
    assertEquals(marked, LSP.json.decodeFromString(DocumentSymbol.serializer(), markedJson))
    assertEquals(marked, LSP.decodeJson.decodeFromString(DocumentSymbol.serializer(), markedJson))
  }

  @Test
  fun `the document symbol result keeps the extension on roots and children`() {
    val parent = plain.copy(name = "p", kind = SymbolKind.Method, intellij = DocumentSymbolIntellij("anonymous"), children = listOf(marked))
    val result = DocumentSymbolResult.DocumentSymbols(listOf(parent))
    val encoded = LSP.json.encodeToString(TextDocuments.DocumentSymbol.resultSerializer, result)
    val decoded = assertIs<DocumentSymbolResult.DocumentSymbols>(LSP.decodeJson.decodeFromString(TextDocuments.DocumentSymbol.resultSerializer, encoded))
    assertEquals(listOf(parent), decoded.value)
  }

  @Test
  fun `unknown keys still decode`() {
    val json = """{"name":"a","kind":12,"range":$r,"selectionRange":$r,"intellij":{"origin":"lambda","next":1},"other":{"x":[1]}}"""
    assertEquals(marked, LSP.json.decodeFromString(DocumentSymbol.serializer(), json))
    assertEquals(marked, LSP.decodeJson.decodeFromString(DocumentSymbol.serializer(), json))
    val result = LSP.decodeJson.decodeFromString(TextDocuments.DocumentSymbol.resultSerializer, "[$json]")
    assertEquals(listOf(marked), assertIs<DocumentSymbolResult.DocumentSymbols>(result).value)
  }
}
