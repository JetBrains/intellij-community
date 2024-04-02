// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment")

package com.intellij.openapi.util.registry

import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.ExtensionPointPriorityListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.RequiredElement
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
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
    @JvmStatic
    fun addKeysFromPlugins() {
      val point = (ApplicationManager.getApplication().extensionArea)
        .getExtensionPoint<RegistryKeyBean>("com.intellij.registryKey") as ExtensionPointImpl
      Registry.setKeys(HashMap<String, RegistryKeyDescriptor>().let { mutator ->
        point.processUnsortedWithPluginDescriptor { bean, pluginDescriptor ->
          val descriptor = createRegistryKeyDescriptor(bean, pluginDescriptor)
          putNewDescriptorConsideringOverrides(mutator, descriptor)
        }
        java.util.Map.copyOf(mutator)
      })

      point.addExtensionPointListener(object : ExtensionPointListener<RegistryKeyBean>, ExtensionPointPriorityListener {
        override fun extensionAdded(extension: RegistryKeyBean, pluginDescriptor: PluginDescriptor) {
          val descriptor = createRegistryKeyDescriptor(extension, pluginDescriptor)
          Registry.mutateContributedKeys { oldMap ->
            val newMap = HashMap<String, RegistryKeyDescriptor>(oldMap.size + 1)
            newMap.putAll(oldMap)
            putNewDescriptorConsideringOverrides(newMap, descriptor)
            java.util.Map.copyOf(newMap)
          }
        }
      }, false, null)

      point.addExtensionPointListener(object : ExtensionPointListener<RegistryKeyBean> {
        override fun extensionRemoved(extension: RegistryKeyBean, pluginDescriptor: PluginDescriptor) {
          pendingRemovalKeys.add(extension.key)
        }
      }, false, null)

      // TODO: Process key removal properly: if override is removed then the overridden value should be re-instantiated
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
                                   extension.overrides,
                                   pluginId)
    }

    private fun putNewDescriptorConsideringOverrides(map: MutableMap<String, RegistryKeyDescriptor>, newDescriptor: RegistryKeyDescriptor) {
      val oldDescriptor = map[newDescriptor.name]
      if (oldDescriptor == null) {
        // no override
        map.put(newDescriptor.name, newDescriptor)
        return
      }

      when (oldDescriptor.isOverrides to newDescriptor.isOverrides) {
        false to true -> { // typical normal override, just allow it
          // TODO: Check dependencies?
          map.put(newDescriptor.name, newDescriptor)
          // TODO: Check loading order if the descriptor requires restart?
          // TODO: ↑ this should only be a concern for dynamically-loaded plugins;
          //       the others we can process in one batch and don't allow anybody to see changes in the registry keys that require restartF
        }
        true to false -> {
          // TODO: Check dependencies?
          // The overriding plugin was loaded first, so no action is required.
        }
        false to false -> {
          // For now, preserve the legacy behavior but report an error.
          map.put(newDescriptor.name, newDescriptor)
          logger<RegistryKeyBean>().error(
            "Conflicting registry key definition for key ${oldDescriptor.name}:" +
            " it was defined by plugin ${oldDescriptor.pluginId}" +
            " but redefined by plugin ${newDescriptor.pluginId}."
          )
        }
        true to true -> {
          logger<RegistryKeyBean>().error(
            "Incorrect registry key override for key ${oldDescriptor.name}:" +
            " both plugins ${oldDescriptor.pluginId} and ${newDescriptor.pluginId} claim to override it."
          )
        }
      }
    }
  }

  @RequiredElement
  @Attribute("key")
  @JvmField var key: String = ""

  @RequiredElement
  @Nls(capitalization = Nls.Capitalization.Sentence)
  @Attribute("description")
  @JvmField var description: String = ""

  @RequiredElement(allowEmpty = true)
  @Attribute("defaultValue")
  @JvmField var defaultValue: String = ""

  @Attribute("restartRequired")
  @JvmField var restartRequired: Boolean = false

  /**
   * Whether this property overrides a property defined somewhere else with the same key.
   *
   * Note we only support **one override** for each registry property. Several conflicting overrides for same key are not supported.
   */
  @Attribute("overrides")
  @JvmField var overrides: Boolean = false
}
