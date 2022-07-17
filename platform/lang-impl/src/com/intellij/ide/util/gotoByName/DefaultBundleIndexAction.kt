// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.gotoByName

import com.intellij.DynamicBundle
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl
import com.intellij.openapi.util.io.FileUtil
import java.io.File

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
        val bundle = DynamicBundle.getResourceBundle(pluginDescriptor?.pluginClassLoader ?: it.javaClass.classLoader, path)

        fun appendKey(key: String, string: String?) {
          if (!bundle.containsKey(key) && !string.isNullOrBlank()) {
            file.appendText("$key=$string\n")
          }
        }

        if (it !is ActionGroup) {
          appendKey("action.$id.text", presentation.text)
          appendKey("action.$id.description", presentation.description)
        } else {
          appendKey("group.$id.text", presentation.text)
          appendKey("group.$id.description", presentation.description)
        }
      }
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}