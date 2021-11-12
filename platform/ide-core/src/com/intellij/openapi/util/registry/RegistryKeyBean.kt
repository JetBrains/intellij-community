// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("ReplacePutWithAssignment")

package com.intellij.openapi.util.registry

import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.ExtensionPointPriorityListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.RequiredElement
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Property
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls

/**
 * Registers custom key for [Registry].
 */
@Property(style = Property.Style.ATTRIBUTE)
class RegistryKeyBean private constructor() {
  companion object {
    // Since the XML parser removes all the '\n' chars joining indented lines together,
    // we can't really tell whether multiple whitespaces actually refer to indentation spaces or just regular ones.
    @NonNls
    private val CONSECUTIVE_SPACES_REGEX = """\s{2,}""".toRegex()

    private val pendingRemovalKeys = HashSet<String>()

    @ApiStatus.Internal
    fun addKeysFromPlugins() {
      val point = (ApplicationManager.getApplication().extensionArea as ExtensionsAreaImpl)
        .getExtensionPoint<RegistryKeyBean>("com.intellij.registryKey")
      val contributedKeys = HashMap<String, RegistryKeyDescriptor>(point.size())
      point.processWithPluginDescriptor(false) { bean, pluginDescriptor ->
        val descriptor = createRegistryKeyDescriptor(bean, pluginDescriptor)
        contributedKeys.put(descriptor.name, descriptor)
      }
      Registry.setKeys(java.util.Map.copyOf(contributedKeys))

      point.addExtensionPointListener(object : ExtensionPointListener<RegistryKeyBean>, ExtensionPointPriorityListener {
        override fun extensionAdded(extension: RegistryKeyBean, pluginDescriptor: PluginDescriptor) {
          val descriptor = createRegistryKeyDescriptor(extension, pluginDescriptor)
          Registry.mutateContributedKeys { oldMap ->
            val newMap = HashMap<String, RegistryKeyDescriptor>(oldMap.size + 1)
            newMap.putAll(oldMap)
            newMap.put(descriptor.name, descriptor)
            java.util.Map.copyOf(newMap)
          }
        }
      }, false, null)

      point.addExtensionPointListener(object : ExtensionPointListener<RegistryKeyBean> {
        override fun extensionRemoved(extension: RegistryKeyBean, pluginDescriptor: PluginDescriptor) {
          pendingRemovalKeys.add(extension.key)
        }
      }, false, null)

      ApplicationManager.getApplication().messageBus.connect().subscribe(DynamicPluginListener.TOPIC, object : DynamicPluginListener {
        override fun pluginUnloaded(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
          Registry.mutateContributedKeys { oldMap ->
            val newMap = HashMap<String, RegistryKeyDescriptor>(oldMap.size - pendingRemovalKeys.size)
            for (entry in oldMap) {
              if (!pendingRemovalKeys.contains(entry.key)) {
                newMap.put(entry.key, entry.value)
              }
            }
            java.util.Map.copyOf(newMap)
          }
          pendingRemovalKeys.clear()
        }
      })
    }

    private fun createRegistryKeyDescriptor(extension: RegistryKeyBean, pluginDescriptor: PluginDescriptor): RegistryKeyDescriptor {
      val pluginId = pluginDescriptor.pluginId.idString
      return RegistryKeyDescriptor(extension.key,
                                   StringUtil.unescapeStringCharacters(extension.description.replace(CONSECUTIVE_SPACES_REGEX, " ")),
                                   extension.defaultValue, extension.restartRequired,
                                   pluginId)
    }
  }

  @RequiredElement
  @Attribute("key")
  @JvmField var key = ""

  @RequiredElement
  @Nls(capitalization = Nls.Capitalization.Sentence)
  @Attribute("description")
  @JvmField var description = ""

  @RequiredElement(allowEmpty = true)
  @Attribute("defaultValue")
  @JvmField var defaultValue = ""

  @Attribute("restartRequired")
  @JvmField var restartRequired = false
}