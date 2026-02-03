// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.settings

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginAware
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.RequiredElement
import com.intellij.util.xmlb.annotations.Attribute
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
class SettingsComponentDescriptor: PluginAware {
  @Attribute("service")
  @RequiredElement
  lateinit var implementationClass: String

  lateinit var pluginDescriptor: PluginDescriptor
    private set

  override fun setPluginDescriptor(pluginDescriptor: PluginDescriptor) {
    this.pluginDescriptor = pluginDescriptor
  }

  override fun toString() = "SettingsComponentDescriptor(implementationClass=$implementationClass, pluginDescriptor=$pluginDescriptor)"

  companion object {
    val APPLICATION_EP_NAME = ExtensionPointName.create<SettingsComponentDescriptor>("com.intellij.applicationSettings")
    val PROJECT_EP_NAME = ExtensionPointName.create<SettingsComponentDescriptor>("com.intellij.projectSettings")
  }
}