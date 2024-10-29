// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment")

package com.intellij.openapi.util.registry

import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
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

    @ApiStatus.Internal
    const val KEY_CONFLICT_LOG_CATEGORY: String = "com.intellij.openapi.util.registry.overrides"

    @ApiStatus.Internal
    fun addKeysFromPlugins() {
      val point = (ApplicationManager.getApplication().extensionArea)
        .getExtensionPoint<RegistryKeyBean>("com.intellij.registryKey") as ExtensionPointImpl
      Registry.setContributedKeys(HashMap<String, RegistryKeyDescriptor>().let { mutator ->
        point.processUnsortedWithPluginDescriptor { bean, pluginDescriptor ->
          val descriptor = createRegistryKeyDescriptor(bean, pluginDescriptor)
          putNewDescriptorConsideringOverrides(map = mutator, newDescriptor = descriptor, isDynamic = false)
        }
        java.util.Map.copyOf(mutator)
      })

      point.addExtensionPointListener(object : ExtensionPointListener<RegistryKeyBean>, ExtensionPointPriorityListener {
        override fun extensionAdded(extension: RegistryKeyBean, pluginDescriptor: PluginDescriptor) {
          val descriptor = createRegistryKeyDescriptor(extension, pluginDescriptor)
          Registry.mutateContributedKeys { oldMap ->
            val newMap = HashMap<String, RegistryKeyDescriptor>(oldMap.size + 1)
            newMap.putAll(oldMap)
            putNewDescriptorConsideringOverrides(map = newMap, newDescriptor = descriptor, isDynamic = true)
            java.util.Map.copyOf(newMap)
          }
        }
      }, false, null)

      ApplicationManager.getApplication().messageBus.connect().subscribe(DynamicPluginListener.TOPIC, object : DynamicPluginListener {
        override fun pluginUnloaded(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
          Registry.mutateContributedKeys { oldMap ->
            val newMap = HashMap<String, RegistryKeyDescriptor>(oldMap.size)
            for (entry in oldMap) {
              if (entry.value.pluginId != pluginDescriptor.pluginId.idString) {
                newMap.put(entry.key, entry.value)
              }
            }
            java.util.Map.copyOf(newMap)
          }
        }
      })
    }

    @ApiStatus.Internal
    fun createRegistryKeyDescriptor(extension: RegistryKeyBean, pluginDescriptor: PluginDescriptor): RegistryKeyDescriptor {
      val pluginId = pluginDescriptor.pluginId.idString
      return RegistryKeyDescriptor(extension.key,
                                   StringUtil.unescapeStringCharacters(extension.description.replace(CONSECUTIVE_SPACES_REGEX, " ")),
                                   extension.defaultValue, extension.restartRequired,
                                   extension.overrides,
                                   pluginId)
    }

    @ApiStatus.Internal
    fun putNewDescriptorConsideringOverrides(
      map: MutableMap<String, RegistryKeyDescriptor>,
      newDescriptor: RegistryKeyDescriptor,
      isDynamic: Boolean
    ) {
      val oldDescriptor = map[newDescriptor.name]
      if (oldDescriptor == null) {
        // no override
        map.put(newDescriptor.name, newDescriptor)
        return
      }

      val logger = Logger.getInstance(KEY_CONFLICT_LOG_CATEGORY)
      fun emitRegistryKeyWarning(message: String) {
        logger.warn(message)
      }

      when (oldDescriptor.isOverrides to newDescriptor.isOverrides) {
        false to true -> { // a normal override, allow it for non-dynamic usages
          if (isDynamic) {
            emitRegistryKeyWarning(
              "A dynamically-loaded plugin ${newDescriptor.pluginId} is forbidden to override" +
              " the registry key ${newDescriptor.name} introduced by ${oldDescriptor.pluginId}." +
              " Consider implementing the functionality in another way," +
              " e.g. declare and implement an extension to customize the required behavior dynamically."
            )
          } else {
            val overrider = newDescriptor.pluginId
            val overridden = oldDescriptor.pluginId
            logger.info("Plugin $overrider overrides the registry key ${newDescriptor.name} declared by plugin $overridden.")
            map.put(newDescriptor.name, newDescriptor)
          }
        }
        true to false -> {
          // The overriding plugin was loaded first, so no action is required.
        }
        false to false -> {
          // For now, preserve the legacy behavior but report an error.
          map.put(newDescriptor.name, newDescriptor)
          emitRegistryKeyWarning(
            "Conflicting registry key definition for key ${oldDescriptor.name}:" +
            " it was defined by plugin ${oldDescriptor.pluginId}" +
            " but redefined by plugin ${newDescriptor.pluginId}." +
            " Consider adding overrides=\"true\" for one of the plugins," +
            " see the documentation for com.intellij.openapi.util.registry.RegistryKeyBean.overrides for more details."
          )
        }
        true to true -> {
          if (oldDescriptor.defaultValue != newDescriptor.defaultValue) {
            emitRegistryKeyWarning(
              "Incorrect registry key override for key ${oldDescriptor.name}:" +
              " both plugins ${oldDescriptor.pluginId} and ${newDescriptor.pluginId} claim to override it to different defaults."
            )
          }
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
   * Note we only support **one override** for each registry property. Several conflicting overrides for the same key are not supported.
   *
   * Registry override in a dynamically loaded plugin will have no effect.
   */
  @Attribute("overrides")
  @JvmField var overrides: Boolean = false
}
