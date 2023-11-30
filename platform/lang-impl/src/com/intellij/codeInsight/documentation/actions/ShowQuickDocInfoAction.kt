// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.documentation.actions

import com.intellij.codeInsight.hint.HintManagerImpl.ActionToIgnore
import com.intellij.lang.documentation.ide.impl.DocumentationManager
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAware
import com.intellij.platform.ide.documentation.DOCUMENTATION_TARGETS

open class ShowQuickDocInfoAction : AnAction(),
                                    ActionToIgnore,
                                    DumbAware,
                                    PopupAction,
                                    PerformWithDocumentsCommitted {

  init {
    isEnabledInModalContext = true
    @Suppress("LeakingThis")
    setInjectedContext(true)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = e.dataContext.getData(DOCUMENTATION_TARGETS)?.isNotEmpty() ?: false
  }

  override fun actionPerformed(e: AnActionEvent) {
    val dataContext = e.dataContext
    val project = dataContext.getData(CommonDataKeys.PROJECT) ?: return
    DocumentationManager.getInstance(project).actionPerformed(dataContext)
  }

  @Suppress("SpellCheckingInspection")
  companion object {
    const val CODEASSISTS_QUICKJAVADOC_FEATURE: String = "codeassists.quickjavadoc"
    const val CODEASSISTS_QUICKJAVADOC_LOOKUP_FEATURE: String = "codeassists.quickjavadoc.lookup"
    const val CODEASSISTS_QUICKJAVADOC_CTRLN_FEATURE: String = "codeassists.quickjavadoc.ctrln"
  }
}
