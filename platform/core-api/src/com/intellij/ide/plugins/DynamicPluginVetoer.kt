// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

interface DynamicPluginVetoer {
  /**
   * Checks if the plugin can be dynamically unloaded at this moment.
   *
   * Not dispatched for content modules (plugin model V2).
   *
   * @return Error message to display
   *         'null' to let the plugin be unloaded
   */
  fun vetoPluginUnload(pluginDescriptor: IdeaPluginDescriptor): @Nls String?


  @ApiStatus.Internal
  fun vetoPluginLoad(pluginDescriptor: IdeaPluginDescriptor): Boolean = false
}

