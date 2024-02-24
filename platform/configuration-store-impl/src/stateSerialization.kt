// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")

package com.intellij.configurationStore

import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.buildNsUnawareJdom
import com.intellij.platform.settings.*
import com.intellij.serialization.SerializationException
import com.intellij.util.xml.dom.readXmlAsModel
import com.intellij.util.xmlb.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
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
        return rootBinding.deserialize(context = null, element = stateElement, JdomAdapter) as T
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
        (rootBinding as BeanBinding).deserializeInto(bean = mergeInto, element = stateElement)
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
    return binding.serializeProperties(bean = bean, preCreatedElement = null, filter = jdomSerializer.getDefaultSerializationFilter())
  }
  else {
    return (binding as RootBinding).serialize(bean = bean, filter = jdomSerializer.getDefaultSerializationFilter())
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

      if (binding is PrimitiveValueBinding && binding.isPrimitive) {
        if (valueData == null) {
          binding.setValue(bean = result, value = null)
        }
        else {
          val s = Json.parseToJsonElement(valueData.decodeToString()).jsonPrimitive.content
          binding.setValue(bean = result, value = s)
        }
      }
      else if (valueData != null) {
        val element = readXmlAsModel(valueData)
        val l = deserializeBeanFromControllerInto(result = result, element = element, binding = binding)
        if (l != null) {
          //var effectiveL = l
          //if (value.isPartial && oldData != null) {
          //  val oldL = deserializeBeanInto(result = result, element = oldData, binding = binding, checkAttributes = false)
          //  if (oldL != null) {
          //    // XML serialization framework is aware of multi-list, even if an old format (surrounded by a tag) is used
          //    effectiveL = l + oldL
          //  }
          //}
          (binding as MultiNodeBinding).deserializeList(bean = result, elements = l, adapter = XmlDomAdapter)
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
        (binding as MultiNodeBinding).deserializeList(bean = result, elements = l, adapter = JdomAdapter)
      }
    }
  }
  return result
}

internal fun createSettingDescriptor(key: String, pluginId: PluginId, tags: Collection<SettingTag>): SettingDescriptor<ByteArray> {
  return SettingDescriptor(key = key, pluginId = pluginId, tags = tags, serializer = RawSettingSerializerDescriptor)
}
