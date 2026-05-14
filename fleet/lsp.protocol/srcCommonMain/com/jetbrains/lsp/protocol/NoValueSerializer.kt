package com.jetbrains.lsp.protocol

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.NothingSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder

/**
 * Serializer for LSP request payload slots (params / result / error) defined as having
 * no value, typed as [Nothing]?. Accepts any JSON on decode and discards it — tolerates
 * non-conformant peers that send `{}` instead of omitting the field or sending `null`.
 * Encodes as JSON `null`.
 */
object NoValueSerializer : KSerializer<Nothing?> {
    override val descriptor: SerialDescriptor = NothingSerializer().nullable.descriptor

    override fun serialize(encoder: Encoder, value: Nothing?) {
        encoder.encodeNull()
    }

    override fun deserialize(decoder: Decoder): Nothing? {
        // In streaming mode the decoder is parked at the start of the value;
        // consume it so the surrounding decoder advances past this slot.
        // LSP is JSON-RPC, so the decoder is always a JsonDecoder.
        (decoder as JsonDecoder).decodeJsonElement()
        return null
    }
}
