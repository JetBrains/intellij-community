// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.util.messages.impl.DynamicPluginUnloaderCompatibilityLayer
import org.jetbrains.annotations.Nls

internal class FallbackPluginVetoer : DynamicPluginVetoer {
  override fun vetoPluginUnload(pluginDescriptor: IdeaPluginDescriptor): @Nls String? {
    val vetoMessage = DynamicPluginUnloaderCompatibilityLayer.queryPluginUnloadVetoers(pluginDescriptor, ApplicationManager.getApplication().messageBus)
    if (vetoMessage != null) return vetoMessage

    for (project in ProjectManager.getInstance().openProjects) {
      val vetoMessage = DynamicPluginUnloaderCompatibilityLayer.queryPluginUnloadVetoers(pluginDescriptor, project.messageBus)
      if (vetoMessage != null) return vetoMessage
    }
    return null
  }
}