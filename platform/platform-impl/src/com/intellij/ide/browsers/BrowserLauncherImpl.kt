// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.browsers

import com.intellij.CommonBundle
import com.intellij.ide.IdeBundle
import com.intellij.ide.impl.isTrusted
import com.intellij.ide.impl.setTrusted
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.util.NlsContexts.NotificationContent
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.Urls
import org.jetbrains.ide.BuiltInServerManager

open class BrowserLauncherImpl : BrowserLauncherAppless() {
  override fun getDefaultBrowser(): WebBrowser? {
    val browserManager = WebBrowserManager.getInstance()
    return if (browserManager.getDefaultBrowserPolicy() == DefaultBrowserPolicy.FIRST) browserManager.firstActiveBrowser else null
  }

  override fun canBrowse(project: Project?, uri: String): Boolean {
    if (project == null || project.isTrusted()) {
      return true
    }
    val yesLabel = IdeBundle.message("external.link.confirmation.yes.label")
    val trustLabel = IdeBundle.message("external.link.confirmation.trust.label")
    val noLabel = CommonBundle.getCancelButtonText()
    val answer = MessageDialogBuilder
      .Message(title = IdeBundle.message("external.link.confirmation.title"), message = IdeBundle.message("external.link.confirmation.message.0", uri))
      .asWarning()
      .buttons(yesLabel, trustLabel, noLabel)
      .defaultButton(yesLabel)
      .focusedButton(trustLabel)
      .show(project)
    when (answer) {
      yesLabel -> return true
      trustLabel -> { project.setTrusted(true); return true }
      else -> return false
    }
  }

  override fun signUrl(url: String): String {
    val parsedUrl = Urls.parse(url, false)
    val serverManager = BuiltInServerManager.getInstance()
    if (parsedUrl != null && serverManager.isOnBuiltInWebServer(parsedUrl)) {
      if (Registry.`is`("ide.built.in.web.server.activatable", false)) {
        PropertiesComponent.getInstance().setValue("ide.built.in.web.server.active", true)
      }
      return serverManager.addAuthToken(parsedUrl).toExternalForm()
    }
    return url
  }

  override fun substituteBrowser(browserPath: String): WebBrowser? {
    val browserManager = WebBrowserManager.getInstance()
    if (browserManager.getDefaultBrowserPolicy() == DefaultBrowserPolicy.FIRST || SystemInfo.isMac && "open" == browserPath) {
      val firstActiveBrowser = browserManager.firstActiveBrowser
      if (firstActiveBrowser != null) return firstActiveBrowser
    }

    return null
  }

  override fun showError(message: @NotificationContent String?, project: Project?, browser: WebBrowser?, retry: (() -> Unit)?) {
    val content = message ?: IdeBundle.message("unknown.error")
    Notification("BrowserCfgProblems", IdeBundle.message("notification.title.browser.config.problem"), content, NotificationType.WARNING)
      .addAction(NotificationAction.createSimpleExpiring(IdeBundle.message("button.fix")) {
        val browserSettings = BrowserSettings()
        val initializer = browser?.let { Runnable { browserSettings.selectBrowser(it) } }
        if (ShowSettingsUtil.getInstance().editConfigurable(project, browserSettings, initializer)) {
          retry?.invoke()
        }
      })
      .notify(project)
  }
}
