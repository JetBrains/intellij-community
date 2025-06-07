// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.util.messages.Topic

@Deprecated("Use DynamicPluginVetoer instead")
class CannotUnloadPluginException(value: String) : ProcessCanceledException(RuntimeException(value))

interface DynamicPluginListener {
  companion object {
    @JvmField
    @Topic.AppLevel
    val TOPIC: Topic<DynamicPluginListener> = Topic(DynamicPluginListener::class.java, Topic.BroadcastDirection.TO_DIRECT_CHILDREN, true)
  }

  fun beforePluginLoaded(pluginDescriptor: IdeaPluginDescriptor) {
  }

  fun pluginLoaded(pluginDescriptor: IdeaPluginDescriptor) {
  }

  /**
   * @param isUpdate `true` if the plugin is being unloaded as part of an update installation and a new version will be loaded afterwards
   */
  fun beforePluginUnload(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
  }

  fun pluginUnloaded(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
  }

  @Deprecated("Use DynamicPluginVetoer instead")
  @Throws(CannotUnloadPluginException::class)
  fun checkUnloadPlugin(pluginDescriptor: IdeaPluginDescriptor) {
  }
}