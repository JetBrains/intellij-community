// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")

package com.intellij.configurationStore

import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.buildNsUnawareJdom
import com.intellij.platform.settings.*
import com.intellij.serialization.ClassUtil
import com.intellij.serialization.SerializationException
import com.intellij.util.xmlb.*
import org.jdom.Element

@Suppress("DEPRECATION", "UNCHECKED_CAST")
internal fun <T : Any> deserializeStateWithController(
  stateElement: Element?,
  stateClass: Class<T>,
  mergeInto: T?,
  controller: SettingsController?,
  componentName: String,
  pluginId: PluginId,
): T? {
  if (stateClass === Element::class.java) {
    return deserializeAsJdomElement(
      controller = controller,
      componentName = componentName,
      pluginId = pluginId,
      stateElement = stateElement,
    ) as T? ?: mergeInto
  }
  else if (com.intellij.openapi.util.JDOMExternalizable::class.java.isAssignableFrom(stateClass)) {
    if (stateElement == null) {
      return mergeInto
    }
    if (mergeInto != null) {
      LOG.error("State is ${stateClass.name}, merge into is $mergeInto, state element text is ${JDOMUtil.writeElement(stateElement)}")
    }
    return deserializeJdomExternalizable(stateClass = stateClass, stateElement = stateElement)
  }

  if (stateElement == null && controller == null) {
    return mergeInto
  }

  val serializer = __platformSerializer()
  // KotlinxSerializationBinding here is possible, do not cast to BeanBinding
  val rootBinding = serializer.getRootBinding(stateClass) as NotNullDeserializeBinding
  try {
    if (mergeInto == null) {
      if (rootBinding !is BeanBinding || controller == null) {
        if (stateElement == null) {
          return null
        }
        return rootBinding.deserialize(context = null, element = stateElement) as T
      }

      return getXmlSerializationState<T>(
        oldData = stateElement,
        mergeInto = null,
        rootBinding = rootBinding,
        componentName = componentName,
        pluginId = pluginId,
        controller = controller,
      )
    }
    else {
      if (controller == null) {
        if (stateElement == null) {
          return mergeInto
        }
        (rootBinding as BeanBinding).deserializeInto(result = mergeInto, element = stateElement)
      }
      else {
        return getXmlSerializationState(
          oldData = stateElement,
          mergeInto = mergeInto,
          rootBinding = rootBinding as BeanBinding,
          componentName = componentName,
          pluginId = pluginId,
          controller = controller,
        )
      }
      return mergeInto
    }
  }
  catch (e: SerializationException) {
    throw e
  }
  catch (e: Exception) {
    throw XmlSerializationException("Cannot deserialize class ${stateClass.name}", e)
  }
}

private fun deserializeAsJdomElement(
  controller: SettingsController?,
  componentName: String,
  pluginId: PluginId,
  stateElement: Element?,
): Element? {
  try {
    val tags = java.util.List.of(PersistenceStateComponentPropertyTag(componentName))
    val key = createSettingDescriptor(key = componentName, pluginId = pluginId, tags = tags)
    val item = controller?.doGetItem(key) ?: GetResult.inapplicable()
    if (item.isResolved) {
      val xmlData = item.get() ?: return null
      return buildNsUnawareJdom(xmlData)
    }
  }
  catch (e: Throwable) {
    LOG.error("Cannot deserialize value for $componentName", e)
  }
  return stateElement
}

internal fun serializeForController(bean: Any): Element? {
  val aClass = bean.javaClass
  assert(aClass !== Element::class.java)
  val serializer = __platformSerializer()
  val binding = serializer.getRootBinding(aClass)
  if (binding is BeanBinding) {
    return binding.serializeInto(bean = bean, preCreatedElement = null, filter = jdomSerializer.getDefaultSerializationFilter())
  }
  else {
    return binding.serialize(bean = bean, context = null, filter = jdomSerializer.getDefaultSerializationFilter()) as Element
  }
}

private fun <T : Any> getXmlSerializationState(
  oldData: Element?,
  mergeInto: T?,
  rootBinding: BeanBinding,
  componentName: String,
  pluginId: PluginId,
  controller: SettingsController,
): T? {
  var result = mergeInto
  val bindings = rootBinding.bindings!!

  val keyTags = java.util.List.of(PersistenceStateComponentPropertyTag(componentName))
  for (binding in bindings) {
    val key = createSettingDescriptor(key = "${componentName}.${binding.accessor.name}", pluginId = pluginId, tags = keyTags)
    val value = try {
      controller.doGetItem(key)
    }
    catch (e: Throwable) {
      LOG.error("Cannot deserialize value for $key", e)
      GetResult.inapplicable()
    }

    if (value.isResolved) {
      val valueData = value.get()

      if (result == null) {
        // create a result only if we have some data - do not return empty state class
        @Suppress("UNCHECKED_CAST")
        result = rootBinding.newInstance() as T
      }

      if (binding is PrimitiveValueBinding && ClassUtil.isPrimitive(binding.accessor.valueClass)) {
        binding.setValue(bean = result, value = valueData?.decodeToString())
      }
      else if (valueData != null) {
        val element = buildNsUnawareJdom(valueData)
        val l = deserializeBeanInto(result = result, element = element, binding = binding, checkAttributes = false)
        if (l != null) {
          var effectiveL = l
          if (value.isPartial && oldData != null) {
            val oldL = deserializeBeanInto(result = result, element = oldData, binding = binding, checkAttributes = false)
            if (oldL != null) {
              // XML serialization framework is aware of multi-list, even if an old format (surrounded by a tag) is used
              effectiveL = l + oldL
            }
          }
          (binding as MultiNodeBinding).deserializeJdomList(context = result, elements = effectiveL)
        }
      }
    }
    else if (oldData != null) {
      if (result == null) {
        // create a result only if we have some data - do not return empty state class
        @Suppress("UNCHECKED_CAST")
        result = rootBinding.newInstance() as T
      }
      val l = deserializeBeanInto(result = result, element = oldData, binding = binding, checkAttributes = true)
      if (l != null) {
        (binding as MultiNodeBinding).deserializeJdomList(context = result, elements = l)
      }
    }
  }
  return result
}

internal fun createSettingDescriptor(key: String, pluginId: PluginId, tags: Collection<SettingTag>): SettingDescriptor<ByteArray> {
  return SettingDescriptor(
    key = key,
    pluginId = pluginId,
    tags = tags,
    serializer = RawSettingSerializerDescriptor,
  )
}
