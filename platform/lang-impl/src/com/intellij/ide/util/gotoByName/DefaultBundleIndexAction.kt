// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util.gotoByName

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
        val text = presentation.text
        val description = presentation.description
        if (id.contains(":")) return@forEach

        if (!text.isNullOrBlank()) {
          file.appendText("action.$id.text=$text\n")
        }
        if (!description.isNullOrBlank()) {
          file.appendText("action.$id.description=$description\n")
        }
      }
  }
}