// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.documentation.ide.actions

import com.intellij.codeInsight.hint.HintManagerImpl.ActionToIgnore
import com.intellij.lang.documentation.ide.impl.openUrl
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

internal class DocumentationViewExternalAction : AnAction(), ActionToIgnore {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = documentationBrowser(e.dataContext)?.currentExternalUrl() != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val browser = documentationBrowser(e.dataContext) ?: return
    val url = browser.currentExternalUrl() ?: return
    openUrl(project, browser.targetPointer, url)
  }
}
