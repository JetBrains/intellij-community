// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.rpc.backend

import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.extensions.RequiredElement
import com.intellij.serviceContainer.BaseKeyedLazyInstance
import com.intellij.util.xmlb.annotations.Attribute
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

/**
 * A declaration of [RemoteApiProvider.EP_NAME].
 *
 * The bean is read from the plugin descriptor without loading [implementation],
 * so the registry routes a call by [apiInterfaces] and constructs the provider on the first call to one of its APIs.
 */
class RemoteApiProviderBean : BaseKeyedLazyInstance<RemoteApiProvider> {
  @RequiredElement
  @Attribute("implementation")
  lateinit var implementation: String

  /** The comma-separated FQNs of the API interfaces that the provider registers. */
  @RequiredElement
  @Attribute("apiInterfaces")
  @JvmField
  var apiInterfaces: String? = null

  @Suppress("unused") // Platform creates bean via reflection.
  @ApiStatus.Internal
  constructor()

  /** Registers [provider] without a declaration. The registry knows its APIs only after it runs `remoteApis()`. */
  @TestOnly
  @ApiStatus.Internal
  constructor(provider: RemoteApiProvider) : super(provider) {
    implementation = provider.javaClass.name
    // a programmatic registration has no declaring plugin, so the bean names the platform, as the extension point does for an object
    setPluginDescriptor(DefaultPluginDescriptor(PluginId.getId("com.intellij"), provider.javaClass.classLoader))
  }

  @ApiStatus.Internal
  override fun getImplementationClassName(): String = implementation
}
