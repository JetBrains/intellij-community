// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.backend.workspace

import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.cl.PluginAwareClassLoader
import com.intellij.platform.workspace.storage.impl.forgetWorkspaceEntityClasses

/**
 * Drops the entity classes of unloaded modules from the class id registry of the workspace storage.
 */
internal class WorkspaceEntityClassesUnloadListener : DynamicPluginListener {
  override fun pluginsUnloaded() {
    forgetWorkspaceEntityClasses { isOfUnloadingModule(it) }
  }
}

private fun isOfUnloadingModule(aClass: Class<*>): Boolean {
  return (aClass.classLoader as? PluginAwareClassLoader)?.state == PluginAwareClassLoader.UNLOAD_IN_PROGRESS
}
