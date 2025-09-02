// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util.openmap

import fleet.util.serialization.DataSerializer
import fleet.util.serialization.DefaultJson
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable(with = SerializedValue.Serializer::class)
class SerializedValue private constructor(private var state: State) {

  private sealed interface State {
    class Serialized(val json: JsonElement) : State
    class Deserialized<T>(val value: T, val serializer: KSerializer<T>) : State
  }

  internal class Serializer : DataSerializer<SerializedValue, JsonElement>(JsonElement.serializer()) {
    override fun fromData(data: JsonElement): SerializedValue = SerializedValue.fromSerializedValue(data)
    override fun toData(value: SerializedValue): JsonElement = value.json
  }

  companion object {
    fun <T> fromDeserializedValue(value: T, serializer: KSerializer<T>): SerializedValue =
      SerializedValue(State.Deserialized(value, serializer))

    fun fromSerializedValue(value: JsonElement): SerializedValue =
      SerializedValue(State.Serialized(value))
  }

  val json: JsonElement by lazy {
    when (val state = state) {
      is State.Deserialized<*> -> DefaultJson.encodeToJsonElement((state as State.Deserialized<Any?>).serializer, state.value)
      is State.Serialized -> state.json
    }
  }

  fun <T> get(serializer: KSerializer<T>): T =
    when (val state = state) {
      is State.Deserialized<*> -> state.value as T
      is State.Serialized ->
        DefaultJson.decodeFromJsonElement(serializer, state.json).also { value ->
          this.state = State.Deserialized(value, serializer)
        }
    }

  override fun equals(other: Any?): Boolean =
    other is SerializedValue && json == other.json

  override fun hashCode(): Int =
    json.hashCode()

  override fun toString(): String =
    "SerializedValue(${
      runCatching { json.toString() }.getOrElse { 
        "Failed to serialize value ${(state as State.Deserialized<*>).value} due to $it"
      }
    })"
}