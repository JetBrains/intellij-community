// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal

import com.intellij.ide.fileTemplates.impl.FileTemplateManagerImpl
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval

@Deprecated("Should be deleted shortly, was required as quick workaround for those who broke their config right after 232 branch creation")
@ScheduledForRemoval
class ClearFileTemplateNameInternalAction: AnAction("Clear Kotlin File Template Names") {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project!!
    FileTemplateManagerImpl.getInstance(project).internalTemplates.filter { it.name.startsWith("Kotlin ") }.forEach {
      it.fileName = ""
    }
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.project != null
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}