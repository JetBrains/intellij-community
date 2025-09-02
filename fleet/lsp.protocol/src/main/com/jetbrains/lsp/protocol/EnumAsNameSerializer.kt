package com.jetbrains.lsp.protocol

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

open class EnumAsNameSerializer<T : Enum<*>>(
    serialName: String,
    val serialize: (v: T) -> String,
    val deserialize: (v: String) -> T,
) : KSerializer<T> {

    constructor(
        enumClass: KClass<T>,
        field: KProperty1<T, String>,
    ) : this(
        serialName = enumClass.simpleName!!,
        serialize = { field.get(it) },
        deserialize = { name -> enumClass.java.enumConstants.first { field.get(it) == name } }
    )

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(serialName, PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: T) {
        encoder.encodeString(serialize(value))
    }

    override fun deserialize(decoder: Decoder): T {
        val v = decoder.decodeString()
        return deserialize(v)
    }
}