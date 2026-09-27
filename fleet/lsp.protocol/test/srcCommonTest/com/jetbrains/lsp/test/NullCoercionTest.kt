package com.jetbrains.lsp.test

import com.jetbrains.lsp.protocol.CodeActionRegistrationOptions
import com.jetbrains.lsp.protocol.Diagnostic
import com.jetbrains.lsp.protocol.FoldingRange
import com.jetbrains.lsp.protocol.LSP
import com.jetbrains.lsp.protocol.OrBoolean
import com.jetbrains.lsp.protocol.RequestMessage
import com.jetbrains.lsp.protocol.ServerCapabilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * `LSP.json` coerces input values (C5): an explicit `null` for a non-nullable property with a default decodes to the default,
 * and an unknown string enum value decodes to the default (or `null` for a nullable property).
 */
class NullCoercionTest {

  @Test
  fun `explicit null jsonrpc decodes to default version`() {
    val message = LSP.json.decodeFromString(RequestMessage.serializer(), """{"jsonrpc":null,"id":1,"method":"m"}""")
    assertEquals("2.0", message.jsonrpc)
  }

  @Test
  fun `unknown string enum value in nullable property decodes to null`() {
    val range = LSP.json.decodeFromString(FoldingRange.serializer(), """{"startLine":1,"endLine":2,"kind":"somethingNew"}""")
    assertNull(range.kind)
    assertEquals(1, range.startLine)
  }

  @Test
  fun `explicit null in nullable property stays null`() {
    val diagnostic = LSP.json.decodeFromString(
      Diagnostic.serializer(),
      """{"range":{"start":{"line":0,"character":0},"end":{"line":0,"character":1}},"message":"m","severity":null,"code":null}""",
    )
    assertNull(diagnostic.severity)
    assertNull(diagnostic.code)
  }

  @Test
  fun `code action provider without kinds decodes`() {
    val caps = LSP.json.decodeFromString(ServerCapabilities.serializer(), """{"codeActionProvider":{"resolveProvider":true}}""")
    val options = assertIs<OrBoolean.Value<CodeActionRegistrationOptions>>(caps.codeActionProvider).value
    assertNull(options.codeActionKinds)
    assertEquals(true, options.resolveProvider)
  }
}
