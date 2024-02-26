// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceGetOrSet")

package com.intellij.configurationStore

import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import com.intellij.platform.settings.*
import com.intellij.serialization.SerializationException
import com.intellij.util.xml.dom.readXmlAsModel
import com.intellij.util.xmlb.*
import kotlinx.serialization.json.*
import org.jdom.Attribute
import org.jdom.Element
import org.jdom.Namespace
import org.jdom.Text
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.VisibleForTesting

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
  val rootBinding = serializer.getRootBinding(stateClass)
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
    val tags = java.util.List.of(PersistenceStateComponentPropertyTag(componentName), OldLocalValueSupplierTag(supplier = lazy {
      stateElement?.let {
        jdomToJson(it)
      }
    }))
    val key = SettingDescriptor(key = componentName, pluginId = pluginId, tags = tags, serializer = JsonElementSettingSerializerDescriptor)
    val item = controller?.doGetItem(key) ?: GetResult.inapplicable()
    if (item.isResolved) {
      val jsonObject = item.get() ?: return null
      return jsonDomToXml(jsonObject.jsonObject)
    }
  }
  catch (e: Throwable) {
    LOG.error("Cannot deserialize value for $componentName", e)
  }
  return stateElement
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
    val key = SettingDescriptor(key = "${componentName}.${binding.propertyName}", pluginId = pluginId, tags = keyTags, serializer = JsonElementSettingSerializerDescriptor)
    val value = try {
      controller.doGetItem(key)
    }
    catch (e: Throwable) {
      LOG.error("Cannot deserialize value for $key", e)
      GetResult.inapplicable()
    }

    if (value.isResolved) {
      val jsonElement = value.get()
      if (jsonElement != null) {
        if (result == null) {
          // create a result only if we have some data - do not return empty state class
          @Suppress("UNCHECKED_CAST")
          result = rootBinding.newInstance() as T
        }

        binding.setFromJson(result, jsonElement)
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
        (binding as MultiNodeBinding).deserializeList(currentValue = result, elements = l, adapter = JdomAdapter)
      }
    }
  }
  return result
}

@Internal
@VisibleForTesting
fun jsonDomToXml(jsonObject: JsonObject): Element {
  val element = Element(true, jsonObject.get("name")!!.jsonPrimitive.content, Namespace.NO_NAMESPACE)
  val content = jsonObject.get("content")?.jsonPrimitive?.content

  val attributes = jsonObject.get("attributes")?.jsonObject
  if (!attributes.isNullOrEmpty()) {
    for ((name, value) in attributes) {
      element.setAttribute(Attribute(true, name, value.jsonPrimitive.content, Namespace.NO_NAMESPACE))
    }
  }

  if (content == null) {
    val children = jsonObject.get("children")?.jsonArray
    if (!children.isNullOrEmpty()) {
      for (child in children) {
        element.addContent(jsonDomToXml(child.jsonObject))
      }
    }
  }
  else {
    element.addContent(Text(true, content))
  }
  return element
}

internal fun jdomToJson(element: Element): JsonElement {
  // todo optimize
  val xmlOutputter = JbXmlOutputter()
  val byteOut = BufferExposingByteArrayOutputStream()
  byteOut.writer().use {
    xmlOutputter.output(element, it)
  }

  return Json.encodeToJsonElement(readXmlAsModel(byteOut.toByteArray()))
}
