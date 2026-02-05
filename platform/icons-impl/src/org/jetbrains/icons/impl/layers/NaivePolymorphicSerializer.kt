// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.icons.impl.layers

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure

abstract class NaivePolymorphicSerializer<Type>(private val name: String): KSerializer<Type> {
  @OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
  override val descriptor: SerialDescriptor by lazy(LazyThreadSafetyMode.PUBLICATION) {
    buildClassSerialDescriptor(name) {
      element("kind", String.serializer().descriptor)
      element(
        "value",
        buildSerialDescriptor(name + "#Value", SerialKind.CONTEXTUAL)
      )
    }
  }

  @OptIn(ExperimentalSerializationApi::class)
  override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): Type {
    return decoder.decodeStructure(descriptor) {
      var type: String? = null
      var value: Any? = null
      if (decodeSequentially()) {
        return@decodeStructure decodeSequentially(this)
      }

      mainLoop@ while (true) {
        when (val index = decodeElementIndex(descriptor)) {
          CompositeDecoder.DECODE_DONE -> {
            break@mainLoop
          }
          0 -> {
            type = decodeStringElement(descriptor, index)
          }
          1 -> {
            type = requireNotNull(type) { "Cannot read polymorphic value before its type token" }
            value = decodeValue(descriptor, index, type)
          }
          else -> throw _root_ide_package_.kotlinx.serialization.SerializationException(
            "Invalid index in polymorphic deserialization of " +
            (type ?: "unknown class") +
            "\n Expected 0, 1 or DECODE_DONE(-1), but found $index"
          )
        }
      }
      @Suppress("UNCHECKED_CAST")
      requireNotNull(value) { "Value has not been read for type $type" } as Type
    }
  }

  private fun decodeSequentially(compositeDecoder: CompositeDecoder): Type {
    val type = compositeDecoder.decodeStringElement(descriptor, 0)
    return compositeDecoder.decodeValue(descriptor, 1, type)
  }

  override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: Type) {
    encoder.encodeStructure(descriptor) {
      encodeStringElement(descriptor, 0, getActualType(value))
      encodeValue(descriptor, 1, value)
    }
  }

  protected abstract fun getActualType(value: Type): String
  protected abstract fun CompositeEncoder.encodeValue(descriptor: SerialDescriptor, index: Int, value: Type)
  protected abstract fun CompositeDecoder.decodeValue(descriptor: SerialDescriptor, index: Int, type: String): Type
}