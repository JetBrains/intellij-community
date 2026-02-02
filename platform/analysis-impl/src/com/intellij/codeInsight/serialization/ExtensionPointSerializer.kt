// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.serialization

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
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import org.jetbrains.annotations.ApiStatus
import kotlin.reflect.KClass

// TODO IJPL-207762 mark experimental
@ApiStatus.Internal
abstract class ExtensionPointSerializer<Target : Any, Descriptor : Any>(
  private val epName: ExtensionPointName<ExtensionPointSerializerBean>,
  private val descriptorClass: KClass<Descriptor>,
) : KSerializer<Descriptor> {

  fun toDescriptor(target: Target): Descriptor? {
    val fqn = target.javaClass.name
    val helper = findSerializerHelper(fqn) ?: return null
    return helper.converter.toDescriptor(target)
  }

  private fun findNotNullSerializerHelper(fqn: String): SerializationHelper<Target, Descriptor> =
    findSerializerHelper(fqn) ?: error("Cannot find serializer for $fqn")

  private fun findSerializerHelper(fqn: String): SerializationHelper<Target, Descriptor>? {
    // todo maybe cache serializers?
    val bean = epName.extensionList.find { bean ->
      bean.implementationClass == fqn || bean.descriptor == fqn
    } ?: return null

    @Suppress("UNCHECKED_CAST")
    return bean.instance as SerializationHelper<Target, Descriptor>
  }

  private val json: Json // TODO investigate if we need caching here
    get() = Json {
      ignoreUnknownKeys = true
      encodeDefaults = true
      serializersModule = SerializersModule {
        polymorphic(descriptorClass, this@ExtensionPointSerializer) {
          epName.extensionList.map { bean ->
            val actualDescriptorClass = bean.instance.serializableClass.kotlin as KClass<Descriptor>
            subclass(actualDescriptorClass, this@ExtensionPointSerializer)
          }
        }
      }
    }

  override val descriptor: SerialDescriptor = SerialDescriptor(
    serialName = epName.name,
    original = SerializerWrapper.serializer().descriptor
  )

  override fun serialize(encoder: Encoder, value: Descriptor) {
    val fqn = value.javaClass.name
    val (serializer, _) = findNotNullSerializerHelper(fqn)
    val element = json.encodeToJsonElement(serializer, value)
    val wrapper = SerializerWrapper(fqn, element)
    encoder.encodeSerializableValue(SerializerWrapper.serializer(), wrapper)
  }

  override fun deserialize(decoder: Decoder): Descriptor {
    val wrapper = decoder.decodeSerializableValue(SerializerWrapper.serializer())
    val (serializer, _) = findNotNullSerializerHelper(wrapper.fqn)
    @Suppress("UNCHECKED_CAST")
    return json.decodeFromJsonElement(serializer, wrapper.value) as Descriptor
  }
  //}
}

@Serializable
private data class SerializerWrapper(
  val fqn: String,
  val value: JsonElement,
)

/**
 * Returns the instance of serializer for the given implementation class.
 *
 * TODO IJPL-207762 mark experimental
 */
@ApiStatus.Internal
class ExtensionPointSerializerBean : BaseKeyedLazyInstance<SerializationHelper<Any, Any>>(),
                                     KeyedLazyInstance<SerializationHelper<Any, Any>> {
  @Attribute("target")
  @RequiredElement
  @JvmField
  var implementationClass: String? = null

  @Attribute("descriptor")
  @JvmField
  var descriptor: String? = null

  @Attribute("converter")
  @JvmField
  var converter: String? = null

  override fun getImplementationClassName(): String = throw UnsupportedOperationException()

  override fun createInstance(componentManager: ComponentManager, pluginDescriptor: PluginDescriptor): SerializationHelper<Any, Any> {
    val fqn = descriptor ?: implementationClass
    requireNotNull(fqn) { "implementationClass is not specified for $fqn" }

    val converterClass = converter.takeIf { descriptor != null }

    try {
      val serializer = instantiateSerializer(fqn, componentManager, pluginDescriptor)

      val converter = when (converterClass) {
        null -> IdConverter
        else -> componentManager.instantiateClass<DescriptorConverter<Any, Any>>(converterClass, pluginDescriptor)
      }

      return SerializationHelper(serializer, converter, componentManager.loadClass(fqn, pluginDescriptor))
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

  private fun instantiateSerializer(
    fqn: String,
    componentManager: ComponentManager,
    pluginDescriptor: PluginDescriptor,
  ): KSerializer<Any> {
    val serializableClass = componentManager.loadClass<Any>(fqn, pluginDescriptor)
    val serializer = SerializerSearcher.findSerializer(serializableClass)
    return serializer ?: error("Cannot find serializer in $fqn class")
  }

  override fun getKey(): String = implementationClass!!
}

@ApiStatus.Internal
data class SerializationHelper<Target : Any, Descriptor : Any>(
  val serializer: KSerializer<Any>,
  val converter: DescriptorConverter<Target, Descriptor>,
  val serializableClass: Class<Descriptor>,
)

// TODO IJPL-207762 mark experimental
@ApiStatus.Internal
interface DescriptorConverter<Target : Any, Descriptor : Any> {
  /**
   * @return data transfer object for [target], or null if [target] cannot be serialized
   */
  fun toDescriptor(target: Target): Descriptor?
}

private object IdConverter : DescriptorConverter<Any, Any> {
  override fun toDescriptor(target: Any): Any = target
}