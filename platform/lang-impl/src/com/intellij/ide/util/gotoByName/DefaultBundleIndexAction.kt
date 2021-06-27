// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.gotoByName

import com.intellij.DynamicBundle
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl
import com.intellij.openapi.util.io.FileUtil
import java.io.File
import java.util.*

class DefaultBundleIndexAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val actionManager = ActionManager.getInstance()
    val file = File(FileUtil.expandUserHome("~/DefaultActionsBundle.properties")).apply { createNewFile() }

    (actionManager as ActionManagerImpl).actionIds
      .mapNotNull { actionManager.getAction(it) }
      .forEach {
        val presentation = it.templatePresentation.clone()
        val event = AnActionEvent(null, DataContext.EMPTY_CONTEXT, ActionPlaces.ACTION_SEARCH, presentation,
                                  actionManager, 0).apply { setInjectedContext(it.isInInjectedContext) }
        ActionUtil.lastUpdateAndCheckDumb(it, event, false)

        val id = actionManager.getId(it)
        if (id.contains(":")) return@forEach

        val pluginDescriptor = PluginManagerCore.getPluginDescriptorOrPlatformByClassName(it.javaClass.name)
        val path = pluginDescriptor?.resourceBundleBaseName ?: ActionsBundle.IDEA_ACTIONS_BUNDLE
        val bundle = DynamicBundle.INSTANCE.getResourceBundle(path, pluginDescriptor?.pluginClassLoader ?: it.javaClass.classLoader)

        if (it !is ActionGroup) {
          appendKey(bundle, file, "action.$id.text", presentation.text)
          appendKey(bundle, file, "action.$id.description", presentation.description)
        } else {
          appendKey(bundle, file, "group.$id.text", presentation.text)
          appendKey(bundle, file, "group.$id.description", presentation.description)
        }
      }
  }

  companion object {
    private fun appendKey(bundle: ResourceBundle, file: File, key: String, string: String?) {
      if (!bundle.containsKey(key) && !string.isNullOrBlank()) {
        file.appendText("$key=$string\n")
      }
    }
  }
}