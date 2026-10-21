// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.browsers.actions

import com.intellij.ide.IdeBundle
import com.intellij.ide.browsers.BrowserLauncherAppless
import com.intellij.ide.browsers.SystemDefaultBrowser
import com.intellij.ide.browsers.WebBrowser
import com.intellij.ide.browsers.WebBrowserManager
import com.intellij.ide.browsers.WebBrowserXmlService
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class OpenFileInDefaultBrowserAction : DumbAwareAction() {
  override fun update(e: AnActionEvent) {
    val result = BaseOpenInBrowserAction.Handler.doUpdate(e) ?: return

    var description = templatePresentation.description
    if (WebBrowserXmlService.getInstance().isHtmlFile(result.file)) {
      description += " (" + IdeBundle.message("browser.shortcut") + ")"
    }

    val presentation = e.presentation
    presentation.text = templatePresentation.text
    presentation.description = description

    findUsingBrowser()?.let {
      presentation.icon = it.icon
    }

    if (e.isFromContextMenu) {
      presentation.isVisible = presentation.isEnabled
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun actionPerformed(e: AnActionEvent) {
    BaseOpenInBrowserAction.Handler.openInBrowser(e, findUsingBrowser())
  }
}

fun findUsingBrowser(): WebBrowser? {
  val browserManager = WebBrowserManager.getInstance()
  val defaultBrowser = browserManager.defaultBrowser
  if (defaultBrowser !== SystemDefaultBrowser) {
    return defaultBrowser
  }
  return if (BrowserLauncherAppless.canUseSystemDefaultBrowserPolicy()) null else browserManager.firstActiveBrowser
}

