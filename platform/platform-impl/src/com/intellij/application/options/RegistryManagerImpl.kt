// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.ArrayUtilRt
import org.jdom.Element
import java.util.*

@State(name = "Registry", storages = [Storage("ide.general.xml")])
private class RegistryManagerImpl : PersistentStateComponent<Element>, RegistryManager {
  override fun `is`(key: String): Boolean {
    return Registry.get(key).asBoolean()
  }

  override fun intValue(key: String) = Registry.get(key).asInteger()

  override fun intValue(key: String, defaultValue: Int): Int {
    return try {
      intValue(key)
    }
    catch (ignore: MissingResourceException) {
      defaultValue
    }
  }

  override fun get(key: String) = Registry.get(key)

  override fun getState() = Registry.getInstance().state

  override fun noStateLoaded() {
    Registry.getInstance().markAsLoaded()
  }

  override fun loadState(state: Element) {
    val registry = Registry.getInstance()
    registry.loadState(state)
    log(registry)
  }

  private fun log(registry: Registry) {
    val userProperties = registry.userProperties
    if (userProperties.size <= (if (userProperties.containsKey("ide.firstStartup")) 1 else 0)) {
      return
    }

    val keys = ArrayUtilRt.toStringArray(userProperties.keys)
    Arrays.sort(keys)
    val builder = StringBuilder("Registry values changed by user: ")
    for (key in keys) {
      if ("ide.firstStartup" == key) {
        continue
      }
      builder.append(key).append(" = ").append(userProperties[key]).append(", ")
    }
    logger<RegistryManager>().info(builder.substring(0, builder.length - 2))
  }
}