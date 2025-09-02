package com.jetbrains.lsp.protocol

import kotlinx.serialization.KSerializer
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.descriptors.*

open class EnumAsIntSerializer<T : Enum<*>>(
    serialName: String,
    val serialize: (v: T) -> Int,
    val deserialize: (v: Int) -> T
) : KSerializer<T> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(serialName, PrimitiveKind.INT)

    override fun serialize(encoder: Encoder, value: T) {
        encoder.encodeInt(serialize(value))
    }

    override fun deserialize(decoder: Decoder): T {
        val v = decoder.decodeInt()
        return deserialize(v)
    }
}