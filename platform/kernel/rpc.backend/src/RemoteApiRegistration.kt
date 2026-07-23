// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.rpc.backend

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginAware
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.RequiredElement
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Transient
import fleet.rpc.RemoteApi

/**
 * Registers the backend implementation of an [Rpc][fleet.rpc.Rpc] interface, by naming the interface and
 * its implementation in `plugin.xml` instead of writing a [com.intellij.platform.rpc.backend.RemoteApiProvider]:
 * ```xml
 * <platform.rpc.backend.remoteApi
 *     apiInterface="com.example.shared.MyRemoteApi"
 *     implementationClass="com.example.backend.MyRemoteApiImpl"/>
 * ```
 */
internal class RemoteApiRegistration : PluginAware {
  @RequiredElement
  @Attribute("apiInterface")
  lateinit var apiInterface: String

  @RequiredElement
  @Attribute("implementationClass")
  lateinit var implementationClass: String

  private lateinit var pluginDescriptor: PluginDescriptor

  @Transient
  override fun setPluginDescriptor(pluginDescriptor: PluginDescriptor) {
    this.pluginDescriptor = pluginDescriptor
  }

  fun loadApiInterface(): Class<*> {
    try {
      return ApplicationManager.getApplication().loadClass<Any>(apiInterface, pluginDescriptor)
    }
    catch (e: ClassNotFoundException) {
      throw ClassNotFoundException(
        "RPC API interface '$apiInterface' declared in a <platform.rpc.backend.remoteApi> extension of plugin " +
        "'${pluginDescriptor.pluginId}' could not be found.\n" +
        "The module where this extension is declared should have a dependency on the module that " +
        "declares '$apiInterface'.\n" +
        "Cause: ${e.message}"
      )
    }
  }

  fun createImplementation(): RemoteApi<Unit> {
    return ApplicationManager.getApplication().instantiateClass(implementationClass, pluginDescriptor)
  }

  companion object {
    val EP_NAME: ExtensionPointName<RemoteApiRegistration> = ExtensionPointName.create("com.intellij.platform.rpc.backend.remoteApi")
  }
}
