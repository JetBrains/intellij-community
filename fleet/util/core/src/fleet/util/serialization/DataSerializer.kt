// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import fleet.multiplatform.shims.ThreadLocal

abstract class DelegateSerializer<T, D>(
  val t2d: (T) -> D,
  dSerializer: KSerializer<D>,
  val d2t: (D) -> T,
) : DataSerializer<T, D>(dSerializer) {
  final override fun fromData(data: D): T = d2t(data)

  final override fun toData(value: T): D = t2d(value)
}

abstract class DataSerializer<T, D>(private val dataSerializer: KSerializer<D>) : KSerializer<T> {
  abstract fun fromData(data: D): T
  abstract fun toData(value: T): D

  override val descriptor: SerialDescriptor
    get() = dataSerializer.descriptor

  override fun deserialize(decoder: Decoder): T {
    return fromData(dataSerializer.deserialize(decoder))
  }

  override fun serialize(encoder: Encoder, value: T) {
    dataSerializer.serialize(encoder, toData(value))
  }
}

abstract class StringSerializer<T>(val toString: (T) -> String,
                                   val fromString: (String) -> T) : DataSerializer<T, String>(String.serializer()) {
  override fun fromData(data: String): T {
    return fromString(data)
  }

  override fun toData(value: T): String {
    return toString(value)
  }
}

val SerializationCallbackThreadLocal: ThreadLocal<((Any) -> Unit)?> = ThreadLocal()

fun <T> withSerializationCallback(callback: (Any) -> Unit, body: () -> T): T {
  return (SerializationCallbackThreadLocal.get()).let { oldRestrictions ->
    try {
      SerializationCallbackThreadLocal.set(callback)
      body()
    }
    finally {
      SerializationCallbackThreadLocal.set(oldRestrictions)
    }
  }
}
