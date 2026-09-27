package com.jetbrains.lsp.protocol

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Serializes an LSP int enum as its code.
 *
 * Decoding is tolerant: a code that [deserialize] does not know (it returns `null`) decodes to [fallback],
 * so a value from a newer protocol version never fails the whole message.
 * Lists of flags (tags, value sets) use [EnumAsIntListSerializer], which drops unknown codes instead.
 */
open class EnumAsIntSerializer<T : Enum<*>>(
    serialName: String,
    val serialize: (v: T) -> Int,
    val deserialize: (v: Int) -> T?,
    val fallback: T,
) : KSerializer<T> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(serialName, PrimitiveKind.INT)

    override fun serialize(encoder: Encoder, value: T) {
        encoder.encodeInt(serialize(value))
    }

    override fun deserialize(decoder: Decoder): T {
        return deserialize(decoder.decodeInt()) ?: fallback
    }
}

/**
 * Serializes a list of LSP int enum values; decoding drops the codes the [element] serializer does not know.
 */
open class EnumAsIntListSerializer<T : Enum<*>>(
    private val element: EnumAsIntSerializer<T>,
) : KSerializer<List<T>> {
    private val codes: KSerializer<List<Int>> = ListSerializer(Int.serializer())
    private val values: KSerializer<List<T>> = ListSerializer(element)

    override val descriptor: SerialDescriptor = values.descriptor

    override fun serialize(encoder: Encoder, value: List<T>) {
        values.serialize(encoder, value)
    }

    override fun deserialize(decoder: Decoder): List<T> {
        return codes.deserialize(decoder).mapNotNull(element.deserialize)
    }
}

/**
 * Serializes a [ValueSet] of LSP int enum values; decoding drops the codes the [element] serializer does not know,
 * so the client never reports support for a value it does not have.
 */
open class EnumAsIntValueSetSerializer<T : Enum<*>>(
    private val element: EnumAsIntSerializer<T>,
) : KSerializer<ValueSet<T>> {
    private val codes: KSerializer<ValueSet<Int>> = ValueSet.serializer(Int.serializer())
    private val values: KSerializer<ValueSet<T>> = ValueSet.serializer(element)

    override val descriptor: SerialDescriptor = values.descriptor

    override fun serialize(encoder: Encoder, value: ValueSet<T>) {
        values.serialize(encoder, value)
    }

    override fun deserialize(decoder: Decoder): ValueSet<T> {
        return ValueSet(codes.deserialize(decoder).valueSet?.mapNotNull(element.deserialize))
    }
}
