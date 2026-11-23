// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.cl.PluginAwareClassLoader

/**
 * Drops the clipboard history data of unloaded modules from the local [CopyPasteManagerWithHistory].
 * It listens to [pluginsUnloaded], not to the per-plugin events: a content module of a plugin that stays loaded
 * publishes no [DynamicPluginListener.pluginUnloaded], and only the class loader state names its unload.
 */
internal class ClipboardPluginUnloadListener : DynamicPluginListener {
  override fun pluginsUnloaded() {
    (ClientCopyPasteManager.getCurrentInstance() as? CopyPasteManagerWithHistory)?.forgetDataOf { isOfUnloadingModule(it) }
  }
}

private fun isOfUnloadingModule(aClass: Class<*>): Boolean {
  return (aClass.classLoader as? PluginAwareClassLoader)?.state == PluginAwareClassLoader.UNLOAD_IN_PROGRESS
}
