// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus

@Deprecated("Use DynamicPluginVetoer instead")
class CannotUnloadPluginException(value: String) : ProcessCanceledException(RuntimeException(value))

interface DynamicPluginListener {
  companion object {
    @JvmField
    @Topic.AppLevel
    val TOPIC: Topic<DynamicPluginListener> = Topic(DynamicPluginListener::class.java, Topic.BroadcastDirection.TO_DIRECT_CHILDREN, true)
  }

  /** Invoked once per loading plugin group, before N [beforePluginLoaded] invocations for individual plugins */
  @ApiStatus.Experimental
  fun beforePluginsLoaded() {
  }

  /** Invoked once per loading plugin, before the actual loading started */
  fun beforePluginLoaded(pluginDescriptor: IdeaPluginDescriptor) {
  }

  /** Invoked once per loading plugin, after the actual loading finished, **successfully or not** */
  fun pluginLoaded(pluginDescriptor: IdeaPluginDescriptor) {
  }

  /**
   * Invoked once per loading plugin group, after all the plugins in group are loaded, **successfully or not**, and after
   * all appropriate [pluginLoaded] invocations
   */
  @ApiStatus.Experimental
  fun pluginsLoaded() {
  }


  /** Invoked once per unloading plugin group, before N [beforePluginUnload] invocations for individual plugins */
  @ApiStatus.Experimental
  fun beforePluginsUnloaded() {
  }

  /**
   * Invoked once per unloading plugin, before the actual unloading started
   * @param isUpdate `true` if the plugin is being unloaded as part of an update installation and a new version will be loaded afterwards
   */
  fun beforePluginUnload(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
  }

  /** Invoked once per unloading plugin, after the actual unloading finished, **successfully or not** */
  fun pluginUnloaded(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
  }

  /**
   * Invoked once per unloading plugin group, after all the plugins in group are unloaded, **successfully or not**, and after
   * all appropriate [pluginUnloaded] invocations
   */
  @ApiStatus.Experimental
  fun pluginsUnloaded() {
  }

  @Deprecated("Use DynamicPluginVetoer instead")
  @Throws(CannotUnloadPluginException::class)
  fun checkUnloadPlugin(pluginDescriptor: IdeaPluginDescriptor) {
  }
}