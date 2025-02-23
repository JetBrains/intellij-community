// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.rpc

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.extensions.ExtensionPointName
import fleet.util.openmap.SerializedValue
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal
import kotlin.reflect.KClass

private val LOG = fileLogger()

/**
 * Provides a way to serialize a custom type to [SerializedValue] which will be used to send through Rpc or Rhizome.
 */
@ApiStatus.Internal
abstract class CustomTypeRpcSerializer<T : Any>(internal val serializationClass: KClass<T>) {
  abstract fun serialize(value: T): SerializedValue?

  abstract fun deserialize(serializedValue: SerializedValue): T?

  @ApiStatus.Internal
  companion object {
    val EP_NAME: ExtensionPointName<CustomTypeRpcSerializer<*>> = ExtensionPointName<CustomTypeRpcSerializer<*>>("com.intellij.customTypeRpcSerializer")
  }
}

/**
 * Provides a way to serialize given [value] to [SerializedValue], so it can be passed through Rpc or Rhizome.
 * Later it can be deserialized by [deserializeFromRpc].
 *
 * This function uses [CustomTypeRpcSerializer] extension point implementations only.
 */
@ApiStatus.Internal
fun <ValueClass : Any> serializeToRpc(value: ValueClass): SerializedValue? {
  val serializedValue = CustomTypeRpcSerializer.EP_NAME.extensionList.firstNotNullOfOrNull { serializer ->
    try {
      serializer.takeIf { it.serializationClass.isInstance(value) }?.let {
        @Suppress("UNCHECKED_CAST")
        (it as CustomTypeRpcSerializer<ValueClass>).serialize(value)
      }
    }
    catch (e: Exception) {
      LOG.debug("Error during custom type serialization", e)
      null
    }
  }
  return serializedValue
}

/**
 * Provides a way to deserialize given [serializedValue] to [ValueClass].
 *
 * This function uses [CustomTypeRpcSerializer] extension point implementations only.
 */
internal inline fun <reified ValueClass: Any> deserializeFromRpc(serializedValue: SerializedValue?): ValueClass? {
  return deserializeFromRpc(serializedValue, ValueClass::class)
}

@Internal
fun <ValueClass: Any> deserializeFromRpc(serializedValue: SerializedValue?, valueClass: KClass<ValueClass>): ValueClass? {
  serializedValue ?: return null

  return CustomTypeRpcSerializer.EP_NAME.extensionList.firstNotNullOfOrNull { serializer ->
    try {
      serializer.takeIf { it.serializationClass == valueClass }?.let {
        @Suppress("UNCHECKED_CAST")
        (it as CustomTypeRpcSerializer<ValueClass>).deserialize(serializedValue)
      }
    }
    catch (e: Exception) {
      LOG.warn("Error during custom type deserialization", e)
      null
    }
  }
}
