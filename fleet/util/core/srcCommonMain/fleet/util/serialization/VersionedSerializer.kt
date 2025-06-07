// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*


abstract class VersionedSerializer<T>(private val fallbackSerializer: KSerializer<T>,
                                      private val versionedSerializers: Map<String, KSerializer<T>>,
                                      private val actualVersion: String) : KSerializer<T> {
  companion object {
    internal const val VERSION_KEY = "_key_"
    internal const val VALUE_KEY = "_value_"
  }

  init {
    require(versionedSerializers.containsKey(actualVersion)) {
      "Cannot find serializer for actual version `$actualVersion` among versions `${versionedSerializers.keys}`"
    }
  }

  override val descriptor: SerialDescriptor = JsonElement.serializer().descriptor

  override fun serialize(encoder: Encoder, value: T) {
    val jsonElement = (encoder as JsonEncoder).json.encodeToJsonElement(versionedSerializers[actualVersion]!!, value)
    val versionedJsonElement = when (jsonElement) {
      is JsonObject -> JsonObject(jsonElement + (VERSION_KEY to JsonPrimitive(actualVersion)))
      else -> encoder.json.encodeToJsonElement(VersionedSerializedValue(actualVersion, jsonElement))
    }
    encoder.encodeJsonElement(versionedJsonElement)
  }

  override fun deserialize(decoder: Decoder): T {
    val jsonElement = (decoder as JsonDecoder).decodeJsonElement()
    val version = ((jsonElement as? JsonObject)?.get(VERSION_KEY) as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
    return if (version != null) {
      val serializer = requireNotNull(versionedSerializers[version]) {
        "Cannot find serializer for version `$version` among versions `${versionedSerializers.keys}`"
      }
      val value = jsonElement[VALUE_KEY]
      if (value != null) {
        decoder.json.decodeFromJsonElement(serializer, value)
      }
      else {
        decoder.json.decodeFromJsonElement(serializer, JsonObject(jsonElement.minus(VERSION_KEY)))
      }
    }
    else {
      decoder.json.decodeFromJsonElement(fallbackSerializer, jsonElement)
    }
  }
}

@Serializable
private data class VersionedSerializedValue(@SerialName(VersionedSerializer.VERSION_KEY) val key: String,
                                            @SerialName(VersionedSerializer.VALUE_KEY) val value: JsonElement)

fun <From, To> migrationSerializer(serializer: KSerializer<From>, converter: (From) -> To): KSerializer<To> {
  return object : DataSerializer<To, From>(serializer) {
    override fun fromData(data: From): To {
      return converter(data)
    }

    override fun toData(value: To): From {
      error("Migration serializer must no be used for encoding, only for decoding")
    }
  }
}
