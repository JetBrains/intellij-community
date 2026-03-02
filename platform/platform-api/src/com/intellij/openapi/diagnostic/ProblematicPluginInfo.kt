// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diagnostic

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.ApiStatus

/**
 * Provides information about a plugin which caused an error.
 * This interface is a subset of [com.intellij.openapi.extensions.PluginDescriptor]. In the future, it may be changed to avoid duplication.
 */
@ApiStatus.Experimental
interface ProblematicPluginInfo {
  val pluginId: PluginId
  val isBundled: Boolean
  val allowsBundledUpdate: Boolean
  val name: @NlsSafe String
  val version: @NlsSafe String?
  val organization: @NlsSafe String?
  val vendor: @NlsSafe String?
  val vendorUrl: String?
  val vendorEmail: String?
}

@ApiStatus.Internal
class ProblematicPluginInfoBasedOnDescriptor(val pluginDescriptor: IdeaPluginDescriptor) : ProblematicPluginInfo {
  override val pluginId: PluginId
    get() = pluginDescriptor.pluginId
  override val isBundled: Boolean
    get() = pluginDescriptor.isBundled
  override val allowsBundledUpdate: Boolean
    get() = pluginDescriptor.allowBundledUpdate()
  override val name: @NlsSafe String
    get() = pluginDescriptor.name ?: pluginId.idString
  override val version: @NlsSafe String?
    get() = pluginDescriptor.version
  override val organization: @NlsSafe String?
    get() = pluginDescriptor.organization
  override val vendor: @NlsSafe String?
    get() = pluginDescriptor.vendor
  override val vendorUrl: String?
    get() = pluginDescriptor.vendorUrl
  override val vendorEmail: String?
    get() = pluginDescriptor.vendorEmail
}