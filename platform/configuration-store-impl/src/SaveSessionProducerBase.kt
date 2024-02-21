// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")

package com.intellij.configurationStore

import com.intellij.openapi.components.impl.stores.ComponentStorageUtil
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.WriteExternalException
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import com.intellij.openapi.vfs.LargeFileWriteRequestor
import com.intellij.openapi.vfs.SafeWriteRequestor
import com.intellij.platform.settings.PersistenceStateComponentPropertyTag
import com.intellij.platform.settings.SetResult
import com.intellij.platform.settings.SettingsController
import com.intellij.serialization.SerializationException
import com.intellij.util.xmlb.BeanBinding
import com.intellij.util.xmlb.XmlSerializationException
import org.jdom.Element

abstract class SaveSessionProducerBase : SaveSessionProducer, SafeWriteRequestor, LargeFileWriteRequestor {
  abstract val controller: SettingsController?

  final override fun setState(component: Any?, componentName: String, pluginId: PluginId, state: Any?) {
    if (state == null) {
      setSerializedState(componentName = componentName, element = null)
      return
    }

    val element: Element?
    try {
      element = serializeState(state = state, componentName = componentName, pluginId = pluginId, controller = controller)
    }
    catch (e: WriteExternalException) {
      LOG.debug(e)
      return
    }
    catch (e: Throwable) {
      LOG.error("Unable to serialize $componentName state", e)
      return
    }

    setSerializedState(componentName = componentName, element = element)
  }

  abstract fun setSerializedState(componentName: String, element: Element?)
}

internal fun serializeState(state: Any, componentName: String, pluginId: PluginId, controller: SettingsController?): Element? {
  @Suppress("DEPRECATION")
  when (state) {
    is Element -> {
      if (controller != null) {
        val keyTags = java.util.List.of(PersistenceStateComponentPropertyTag(componentName))
        val key = createSettingDescriptor(key = componentName, pluginId = pluginId, tags = keyTags)

        val xmlOutputter = JbXmlOutputter()
        val byteOut = BufferExposingByteArrayOutputStream()
        byteOut.writer().use {
          xmlOutputter.output(state, it)
        }

        val result = controller.doSetItem(key = key, value = byteOut.toByteArray())
        if (result != SetResult.INAPPLICABLE) {
          return null
        }
      }
      return state
    }
    is com.intellij.openapi.util.JDOMExternalizable -> {
      val element = Element(ComponentStorageUtil.COMPONENT)
      state.writeExternal(element)
      return element
    }
    else -> {
      try {
        val filter = jdomSerializer.getDefaultSerializationFilter()
        val binding = __platformSerializer().getRootBinding(state.javaClass)
        if (binding is BeanBinding) {
          // top level expects not null (null indicates error, an empty element will be omitted)
          return binding.serializeInto(bean = state, preCreatedElement = null, filter = filter)
        }
        else {
          // maybe ArrayBinding
          return binding.serialize(bean = state, filter = filter) as Element
        }
      }
      catch (e: SerializationException) {
        throw e
      }
      catch (e: Exception) {
        throw XmlSerializationException("Can't serialize state (componentName=$componentName, class=${state.javaClass})", e)
      }
    }
  }
}
