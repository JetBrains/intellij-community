// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.images.scientific.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.project.DumbAwareAction
import org.intellij.images.scientific.utils.ScientificUtils
import org.intellij.images.scientific.statistics.ScientificImageActionsCollector

class CopyImageAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val dataContext = e.dataContext
    val provider = PlatformDataKeys.COPY_PROVIDER.getData(dataContext)
    if (provider == null) {
      return
    }
    provider.performCopy(dataContext)
    ScientificImageActionsCollector.logCopyImageInvoked(this)
  }

  override fun update(e: AnActionEvent) {
    val imageFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
    e.presentation.isEnabledAndVisible = imageFile?.getUserData(ScientificUtils.SCIENTIFIC_MODE_KEY) != null
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}