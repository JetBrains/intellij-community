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
  /**
   * @return isBundled or isUpdatedBundledPlugin
   */
  val isBuiltIn: Boolean
  val isImplementationDetail: Boolean
  val isEssential: Boolean
  val allowsBundledUpdate: Boolean
  val name: @NlsSafe String
  val version: @NlsSafe String?
  val organization: @NlsSafe String?
  val vendor: @NlsSafe String?
  val vendorUrl: String?
  val vendorEmail: String?
}

@ApiStatus.Experimental
interface ProblematicPluginInfoWithDescriptor : ProblematicPluginInfo {
  val pluginDescriptor: IdeaPluginDescriptor
}