// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Marker interface which says that decoder resistant to errors in output
 *
 * It means that even when decoder failed to decode current element it still can be used to decode next element
 * It can be useful to successfully decode incomplete data like arrays with errors
 */
interface ResistantJsonDecoder : JsonDecoder

fun <T : Any> ResistantJsonDecoder.decodeOrNull(body: ResistantJsonDecoder.() -> T): T? {
  return try {
    body()
  }
  catch (e: SerializationException) {
    null
  }
}

object JsonElementListResistantSerializer : ListResistantSerializer<JsonElement>(JsonElement.serializer())
object JsonObjectListResistantSerializer : ListResistantSerializer<JsonObject>(JsonObject.serializer())

open class ListResistantSerializer<T : Any>(private val elementSerializer: KSerializer<T>) : KSerializer<List<T>> {
  private val listSerializer = ListSerializer(elementSerializer)

  override val descriptor: SerialDescriptor
    get() = listSerializer.descriptor

  final override fun deserialize(decoder: Decoder): List<T> {
    return buildList {
      decoder.decodeStructure(descriptor) {
        while (true) {
          val idx = decodeElementIndex(descriptor)
          if (idx == CompositeDecoder.DECODE_DONE) break
          if (this is ResistantJsonDecoder) {
            decodeOrNull { decodeSerializableElement(descriptor, idx, elementSerializer) }?.let { add(it) }
          }
          else {
            add(decodeSerializableElement(descriptor, idx, elementSerializer))
          }
        }
      }
    }
  }

  final override fun serialize(encoder: Encoder, value: List<T>) {
    listSerializer.serialize(encoder, value)
  }
}