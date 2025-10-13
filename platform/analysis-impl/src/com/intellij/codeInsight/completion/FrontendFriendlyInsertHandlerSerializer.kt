// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion

import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.RequiredElement
import com.intellij.serviceContainer.BaseKeyedLazyInstance
import com.intellij.util.KeyedLazyInstance
import com.intellij.util.xmlb.annotations.Attribute
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Serializer for [FrontendFriendlyInsertHandler].
 * It uses [ep_name] to collect serializers for all known [FrontendFriendlyInsertHandler] implementations.
 */
internal class FrontendFriendlyInsertHandlerSerializer : KSerializer<FrontendFriendlyInsertHandler> {
  private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
  }

  override val descriptor: SerialDescriptor = SerialDescriptor(
    serialName = "com.intellij.codeInsight.completion.FrontendFriendlyInsertHandlerSerializer",
    original = FFIHWrapper.serializer().descriptor
  )

  override fun serialize(encoder: Encoder, value: FrontendFriendlyInsertHandler) {
    val fqn = value.javaClass.name
    val serializer = findSerializer<FrontendFriendlyInsertHandler>(fqn)
    val element = json.encodeToJsonElement(serializer, value)
    val wrapper = FFIHWrapper(fqn, element)
    encoder.encodeSerializableValue(FFIHWrapper.serializer(), wrapper)
  }

  override fun deserialize(decoder: Decoder): FrontendFriendlyInsertHandler {
    val wrapper = decoder.decodeSerializableValue(FFIHWrapper.serializer())
    val serializer = findSerializer<FrontendFriendlyInsertHandler>(wrapper.fqn)
    return json.decodeFromJsonElement(serializer, wrapper.value)
  }
}

private fun <T : FrontendFriendlyInsertHandler> findSerializer(fqn: String): KSerializer<T> {
  // todo maybe cache serializers?
  val bean = ep_name.extensionList.find { bean ->
    bean.implementationClass == fqn
  }
  if (bean == null) {
    throw IllegalArgumentException("Cannot find serializer for $fqn")
  }
  return bean.instance as KSerializer<T>
}

private val ep_name = ExtensionPointName<FrontendFriendlyInsertHandlerSerializerBean>("com.intellij.completion.frontendFriendlyInsertHandler")

internal class FrontendFriendlyInsertHandlerSerializerBean : BaseKeyedLazyInstance<KSerializer<Any>>(), KeyedLazyInstance<KSerializer<Any>> {
  @Attribute("implementationClass")
  @RequiredElement
  @JvmField
  var implementationClass: String? = null

  override fun getImplementationClassName(): String? = throw UnsupportedOperationException()

  override fun createInstance(componentManager: ComponentManager, pluginDescriptor: PluginDescriptor): KSerializer<Any> {
    val fqn = implementationClass
    requireNotNull(fqn) { "implementationClass is not specified for $fqn" }
    try {
      val clazz = componentManager.loadClass<Any>(fqn, pluginDescriptor)

      val companionField = clazz.getField("Companion")
      companionField.trySetAccessible()
      val companion = companionField.get(null)!!

      val serializerMethod = companion.javaClass.getMethod("serializer")
      serializerMethod.trySetAccessible()
      val serializer = serializerMethod.invoke(companion) as KSerializer<Any>

      return serializer
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Throwable) {
      throw IllegalStateException("Cannot obtain kotlinx-serializer instance of $fqn." +
                                  "Please make sure $fqn has @kotlinx.serialization.Serializable annotation, " +
                                  "and it is compiled with the kotlinx-serialization compiler plugin" +
                                  "(see kotlinx-serialization documentation for details).", e)
    }
  }

  override fun getKey(): String = implementationClass!!
}

@Serializable
private data class FFIHWrapper(
  val fqn: String,
  val value: JsonElement
)