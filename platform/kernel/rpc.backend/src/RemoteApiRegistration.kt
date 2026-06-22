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
import fleet.rpc.RemoteApiDescriptor
import fleet.rpc.remoteApiDescriptorOf

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

  fun loadApiDescriptor(): RemoteApiDescriptor<*> {
    return remoteApiDescriptorOf(loadApiInterface())
  }

  private fun loadApiInterface(): Class<*> {
    return ApplicationManager.getApplication().loadClass<Any>(apiInterface, pluginDescriptor)
  }

  fun createImplementation(): RemoteApi<Unit> {
    return ApplicationManager.getApplication().instantiateClass(implementationClass, pluginDescriptor)
  }

  companion object {
    val EP_NAME: ExtensionPointName<RemoteApiRegistration> = ExtensionPointName.create("com.intellij.platform.rpc.backend.remoteApi")
  }
}
