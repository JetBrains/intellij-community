// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util.openmap

import fleet.util.KPersistentMapSerializer
import fleet.util.serialization.DataSerializer
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentHashMapOf
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer

data class SerializableKey<V : Any, D>(
  val key: String,
  val serializer: KSerializer<V>,
) : Key<V, D>

@Serializable(with = SerializableOpenMapSerializer::class)
data class SerializableOpenMap<D>(internal val m: PersistentMap<String, SerializedValue> = persistentHashMapOf()) : OpenMapView<D> {
  interface Mut<D> {
    fun <T : Any> set(key: SerializableKey<T, D>, v: T)
    fun remove(key: SerializableKey<*, D>)
    fun build(): SerializableOpenMap<D>
  }

  fun <T : Any> assoc(key: SerializableKey<T, D>, v: T): SerializableOpenMap<D> {
    return SerializableOpenMap(m.put(key.key, SerializedValue.fromDeserializedValue(v, key.serializer)))
  }

  fun <T : Any> dissoc(key: SerializableKey<out T, D>): SerializableOpenMap<D> {
    return SerializableOpenMap(m.remove(key.key))
  }

  fun mut(): Mut<D> =
    object : Mut<D> {
      val transient = rawMap.builder()
      override fun <T : Any> set(key: SerializableKey<T, D>, v: T) {
        transient[key.key] = SerializedValue.fromDeserializedValue(v, key.serializer)
      }

      override fun remove(key: SerializableKey<*, D>) {
        transient.remove(key.key)
      }

      override fun build(): SerializableOpenMap<D> =
        SerializableOpenMap(transient.build())
    }

  inline fun update(f: Mut<D>.() -> Unit): SerializableOpenMap<D> =
    mut().apply(f).build()

  override fun <T : Any> get(k: Key<T, in D>): T? {
    val key = k as? SerializableKey<T, D> ?: return null
    return m.get(key.key)?.get(key.serializer)
  }

  val rawMap: PersistentMap<String, SerializedValue>
    get() = m

  fun merge(other: SerializableOpenMap<D>): SerializableOpenMap<D> =
    SerializableOpenMap(m.putAll(other.m))

  fun isEmpty() = m.size == 0

  companion object {
    private val EMPTY = SerializableOpenMap<Nothing>()
    fun <D> empty() = EMPTY as SerializableOpenMap<D>
  }
}

class SerializableOpenMapSerializer : DataSerializer<SerializableOpenMap<*>, PersistentMap<String, SerializedValue>>(
  KPersistentMapSerializer(String.serializer(), SerializedValue.serializer())) {
  override fun fromData(data: PersistentMap<String, SerializedValue>): SerializableOpenMap<*> {
    return SerializableOpenMap<Any>(data)
  }

  override fun toData(value: SerializableOpenMap<*>): PersistentMap<String, SerializedValue> {
    return value.m
  }
}
