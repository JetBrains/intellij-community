package com.jetbrains.lsp.test

import com.jetbrains.lsp.protocol.Diagnostics
import com.jetbrains.lsp.protocol.LSP
import com.jetbrains.lsp.protocol.NoValueSerializer
import com.jetbrains.lsp.protocol.RequestMessage
import com.jetbrains.lsp.protocol.RequestType
import com.jetbrains.lsp.protocol.Shutdown
import com.jetbrains.lsp.protocol.Workspace
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

class NoValueSerializerTest {

    private val nonConformantPayloads: List<JsonElement> = listOf(
        JsonNull,
        JsonObject(emptyMap()),
        JsonObject(mapOf("foo" to JsonPrimitive(1), "bar" to JsonObject(emptyMap()))),
        JsonPrimitive(0),
        JsonPrimitive("anything"),
        JsonArray(listOf(JsonPrimitive(1))),
    )

    @Test
    fun `every Nothing-slot in listed requests is wired through NoValueSerializer`() {
        val requestsWithNothingSlots: List<RequestType<*, *, *>> = listOf(
            Shutdown,
            Workspace.RefreshCodeLenses,
            Workspace.RefreshInlayHints,
            Workspace.RefreshSemanticTokens,
            Diagnostics.Refresh,
        )
        val slots = requestsWithNothingSlots
            .flatMap { (method, paramsSerializer, resultSerializer, errorSerializer) ->
                listOf(
                    "$method#params" to paramsSerializer,
                    "$method#result" to resultSerializer,
                    "$method#error" to errorSerializer,
                )
            }.filter { (_, serializer) ->
                serializer.descriptor.serialName == "kotlin.Nothing?"
            }
        // Shutdown contributes 2 (error is Unit); the other four contribute 3 each → 14.
        assertEquals(14, slots.size, "expected exactly 14 Nothing? slots, found ${slots.size}: ${slots.map { it.first }}")
        for ((name, serializer) in slots) {
            assertSame(NoValueSerializer, serializer, "slot $name should be NoValueSerializer")
        }
    }

    @Test
    fun `NoValueSerializer decodes any JSON payload to null`() {
        for (payload in nonConformantPayloads) {
            assertNull(
                LSP.json.decodeFromJsonElement(NoValueSerializer, payload),
                "expected null for payload $payload",
            )
        }
    }

    @Test
    fun `encodes null to JsonNull`() {
        assertEquals(JsonNull, LSP.json.encodeToJsonElement(NoValueSerializer, null))
    }

    @Test
    fun `streaming decode through RequestMessage tolerates non-conformant shutdown params`() {
        // Original bug repro: TS clients calling sendRequest('shutdown', {}) crashed with
        // SerializationException("'kotlin.Nothing' does not have instances").
        val incoming = """{"jsonrpc":"2.0","id":1,"method":"shutdown","params":{"unexpected":"stuff"}}"""
        val (_, _, method, params) = LSP.json.decodeFromString(RequestMessage.serializer(), incoming)
        assertEquals("shutdown", method)
        assertNotNull(params)
        assertNull(LSP.json.decodeFromJsonElement(Shutdown.paramsSerializer, params))
    }

    @Test
    fun `streaming decode directly through NoValueSerializer consumes the value`() {
        // Without the decodeJsonElement() call inside NoValueSerializer, streaming-mode
        // decoding would leave the parser at the start of the value, breaking nested use.
        assertNull(LSP.json.decodeFromString(NoValueSerializer, """{"foo":1,"bar":{}}"""))
        assertNull(LSP.json.decodeFromString(NoValueSerializer, "null"))
        assertNull(LSP.json.decodeFromString(NoValueSerializer, """[1, 2, 3]"""))
        assertNull(LSP.json.decodeFromString(NoValueSerializer, "42"))
    }
}
