// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.org

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service

/**
 * This is the common service to deal with organizational
 * restrictions in the UI for the plugin management.
 */
@Service(Service.Level.APP)
class PluginManagerConfigurableForOrg {
  companion object {
    @JvmStatic
    fun getInstance(): PluginManagerConfigurableForOrg = service()
  }

  fun allowInstallingPlugin(descriptor: IdeaPluginDescriptor) : Boolean {
    return true
  }

  fun isPluginAllowed(isLocalPlugin: Boolean,
                      descriptor: IdeaPluginDescriptor
  ) : Boolean {
    return true
    //if (isLocalPlugin) return false
    //return descriptor.pluginId.idString.hashCode() % 2 == 0
  }

  fun allowInstallFromDisk(): Boolean = true
}
