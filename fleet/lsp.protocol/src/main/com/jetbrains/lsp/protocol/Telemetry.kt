package com.jetbrains.lsp.protocol

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable(with = JsonObjectOrArraySerializer::class)
sealed interface JsonObjectOrArray {
    @Serializable
    @JvmInline
    value class Object(val value: JsonObject) : JsonObjectOrArray

    @Serializable
    @JvmInline
    value class Array(val value: JsonArray) : JsonObjectOrArray
}

object JsonObjectOrArraySerializer : JsonContentPolymorphicSerializer<JsonObjectOrArray>(JsonObjectOrArray::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<JsonObjectOrArray> {
        return when (element) {
            is JsonObject -> JsonObjectOrArray.Object.serializer()
            is JsonArray -> JsonObjectOrArray.Array.serializer()
            else -> throw SerializationException("Expected either an array of a JSON object. Got $element.")
        }
    }
}

object Telemetry {
    val Event: NotificationType<JsonObjectOrArray> = NotificationType("telemetry/event", JsonObjectOrArray.serializer())
}

