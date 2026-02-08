// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.configurationStore.getStateSpec
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.extensions.PluginId
import com.intellij.platform.settings.DelegatedSettingsController
import com.intellij.platform.settings.GetResult
import com.intellij.platform.settings.PersistenceStateComponentPropertyTag
import com.intellij.platform.settings.SetResult
import com.intellij.platform.settings.SettingDescriptor
import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap

@ApiStatus.Internal
interface LoggingSettingsChangesListener {
  fun performed(event: Event)

  class Event(val change: String)

  companion object {
    @Topic.AppLevel
    val TOPIC: Topic<LoggingSettingsChangesListener> = Topic(LoggingSettingsChangesListener::class.java)
    const val JVM_PROPERTY_KEY: String = "ide.settings.log.persistent.changes"
  }
}

internal class LoggingSettingsController : DelegatedSettingsController {
  private val storage = ConcurrentHashMap<PluginId, ConcurrentHashMap<String, Any>>()
  private val ignoredKeys = setOf("EntryPointsManager", "ProjectPlainTextFileTypeManager")

  init {
    if (!System.getProperty(LoggingSettingsChangesListener.JVM_PROPERTY_KEY, "false").toBoolean()) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override fun <T : Any> getItem(key: SettingDescriptor<T>): GetResult<T?> {
    return GetResult.inapplicable()
  }

  override fun createChild(container: ComponentManager): DelegatedSettingsController {
    return LoggingSettingsController()
  }

  override fun <T : Any> setItem(key: SettingDescriptor<T>, value: T?): SetResult {
    val pluginStorage = storage.computeIfAbsent(key.pluginId) { ConcurrentHashMap() }
    val oldValue = pluginStorage[key.key]
    if (value == null) {
      pluginStorage.remove(key.key)
    }
    else {
      pluginStorage[key.key] = value
    }

    if (!key.key.isIgnored && oldValue != value) {
      reportNewSettingValue(key, value)
    }

    return SetResult.inapplicable()
  }

  private fun <T : Any> reportNewSettingValue(key: SettingDescriptor<T>, value: T?) {
    val stateClass = (key.tags.firstOrNull { it is PersistenceStateComponentPropertyTag } as? PersistenceStateComponentPropertyTag)?.stateClass
                     ?: return

    val stateSpec = getStateSpec(stateClass)
    val storages = stateSpec?.let {
      stateSpec.storages.joinToString(", ") { it.value }
    } ?: "-"

    val message = "key: ${key.key} -- storage: $storages -- value: $value"

    ApplicationManager.getApplication().messageBus.syncPublisher(LoggingSettingsChangesListener.TOPIC)
      .performed(LoggingSettingsChangesListener.Event(message))
  }

  private val String.isIgnored: Boolean get() = ignoredKeys.contains(this)
}

