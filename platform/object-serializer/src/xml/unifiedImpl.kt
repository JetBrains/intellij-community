// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal
@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")
@file:OptIn(IntellijInternalApi::class)

package com.intellij.serialization.xml

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.platform.settings.JsonElementSettingSerializerDescriptor
import com.intellij.platform.settings.SettingDescriptor
import com.intellij.platform.settings.SettingTag
import com.intellij.platform.settings.SettingsController
import com.intellij.util.xmlb.NestedBinding
import com.intellij.util.xmlb.SettingsInternalApi
import com.intellij.util.xmlb.jsonDomToXml
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.cancellation.CancellationException

@SettingsInternalApi
fun deserializeAsJdomElement(
  localValue: Element?,
  controller: SettingsController,
  componentName: String,
  pluginId: PluginId,
  tags: Collection<SettingTag>,
): Element? {
  try {
    val key = SettingDescriptor(
      key = createSettingKey(componentName = componentName, binding = null),
      pluginId = pluginId,
      tags = tags,
      serializer = JsonElementSettingSerializerDescriptor,
    )
    val item = controller.doGetItem(key)
    if (item.isResolved) {
      val value = item.get()
      return if (value == null || value == JsonNull) null else jsonDomToXml(value.jsonObject)
    }
  }
  catch (e: CancellationException) {
    throw e
  }
  catch (e: Throwable) {
    logger<SettingsController>().error("Cannot deserialize value for $componentName", e)
  }
  return localValue
}

@SettingsInternalApi
fun createSettingKey(componentName: String, binding: NestedBinding?): String {
  val normalizedComponentName = componentName.replace('.', '-')
  return if (binding == null) normalizedComponentName else "$normalizedComponentName.${binding.propertyName}"
}