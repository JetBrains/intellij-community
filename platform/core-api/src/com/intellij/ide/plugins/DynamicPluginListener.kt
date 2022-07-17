// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.util.messages.Topic

class CannotUnloadPluginException(value: String) : ProcessCanceledException(RuntimeException(value))

interface DynamicPluginListener {
  companion object {
    @JvmField
    @Topic.AppLevel
    val TOPIC = Topic(DynamicPluginListener::class.java, Topic.BroadcastDirection.TO_DIRECT_CHILDREN, true)
  }

  @JvmDefault
  fun beforePluginLoaded(pluginDescriptor: IdeaPluginDescriptor) {
  }

  @JvmDefault
  fun pluginLoaded(pluginDescriptor: IdeaPluginDescriptor) {
  }

  /**
   * @param isUpdate `true` if the plugin is being unloaded as part of an update installation and a new version will be loaded afterwards
   */
  @JvmDefault
  fun beforePluginUnload(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
  }

  @JvmDefault
  fun pluginUnloaded(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
  }

  /**
   * Checks if the plugin can be dynamically unloaded at this moment.
   * Method should throw [CannotUnloadPluginException] if it isn't possible for some reason.
   *
   * Not dispatched for a content modules (plugin model V2).
   */
  @Throws(CannotUnloadPluginException::class)
  @JvmDefault
  fun checkUnloadPlugin(pluginDescriptor: IdeaPluginDescriptor) {
  }
}