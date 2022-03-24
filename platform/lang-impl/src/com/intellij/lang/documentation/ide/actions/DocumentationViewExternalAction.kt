// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.documentation.ide.actions

import com.intellij.codeInsight.hint.HintManagerImpl.ActionToIgnore
import com.intellij.lang.documentation.ide.impl.DocumentationBrowser
import com.intellij.lang.documentation.ide.impl.openUrl
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext

internal class DocumentationViewExternalAction : AnAction(), ActionToIgnore {

  /**
   * TODO consider exposing [DocumentationBrowser.currentExternalUrl]
   *  in [com.intellij.lang.documentation.ide.DocumentationBrowserFacade]
   *  to get rid of the cast
   */
  private fun browser(dc: DataContext): DocumentationBrowser? = dc.getData(DOCUMENTATION_BROWSER) as? DocumentationBrowser

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = browser(e.dataContext)?.currentExternalUrl() != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val browser = browser(e.dataContext) ?: return
    val url = browser.currentExternalUrl() ?: return
    openUrl(project, browser.targetPointer, url)
  }
}
