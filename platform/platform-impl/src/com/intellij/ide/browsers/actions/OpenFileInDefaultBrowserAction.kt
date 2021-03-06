// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.browsers.actions

import com.intellij.ide.GeneralSettings
import com.intellij.ide.IdeBundle
import com.intellij.ide.browsers.*
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

class OpenFileInDefaultBrowserAction : DumbAwareAction() {
  override fun update(e: AnActionEvent) {
    val result = BaseOpenInBrowserAction.doUpdate(e) ?: return

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

    if (ActionPlaces.isPopupPlace(e.place)) {
      presentation.isVisible = presentation.isEnabled
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    BaseOpenInBrowserAction.openInBrowser(e, findUsingBrowser())
  }
}

fun findUsingBrowser(): WebBrowser? {
  val browserManager = WebBrowserManager.getInstance()
  val defaultBrowserPolicy = browserManager.defaultBrowserPolicy
  if (defaultBrowserPolicy == DefaultBrowserPolicy.FIRST || defaultBrowserPolicy == DefaultBrowserPolicy.SYSTEM && !BrowserLauncherAppless.canUseSystemDefaultBrowserPolicy()) {
    return browserManager.firstActiveBrowser
  }
  else if (defaultBrowserPolicy == DefaultBrowserPolicy.ALTERNATIVE) {
    val path = GeneralSettings.getInstance().browserPath
    if (!path.isNullOrBlank()) {
      val browser = browserManager.findBrowserById(path)
      if (browser == null) {
        for (item in browserManager.activeBrowsers) {
          if (path == item.path) {
            return item
          }
        }
      }
      else {
        return browser
      }
    }
  }
  return null
}