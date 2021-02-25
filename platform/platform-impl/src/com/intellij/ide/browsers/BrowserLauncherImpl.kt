// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.browsers

import com.intellij.ide.IdeBundle
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.AppUIExecutor
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.showOkNoDialog
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.Urls
import org.jetbrains.ide.BuiltInServerManager

open class BrowserLauncherImpl : BrowserLauncherAppless() {
  override fun getEffectiveBrowser(browser: WebBrowser?): WebBrowser? {
    var effectiveBrowser = browser
    if (browser == null) {
      // https://youtrack.jetbrains.com/issue/WEB-26547
      val browserManager = WebBrowserManager.getInstance()
      if (browserManager.getDefaultBrowserPolicy() == DefaultBrowserPolicy.FIRST) {
        effectiveBrowser = browserManager.firstActiveBrowser
      }
    }
    return effectiveBrowser
  }

  override fun signUrl(url: String): String {
    @Suppress("NAME_SHADOWING")
    var url = url
    @Suppress("NAME_SHADOWING")
    val serverManager = BuiltInServerManager.getInstance()
    val parsedUrl = Urls.parse(url, false)
    if (parsedUrl != null && serverManager.isOnBuiltInWebServer(parsedUrl)) {
      if (Registry.`is`("ide.built.in.web.server.activatable", false)) {
        PropertiesComponent.getInstance().setValue("ide.built.in.web.server.active", true)
      }

      url = serverManager.addAuthToken(parsedUrl).toExternalForm()
    }
    return url
  }

  override fun openWithExplicitBrowser(url: String, browserPath: String?, project: Project?) {
    val browserManager = WebBrowserManager.getInstance()
    if (browserManager.getDefaultBrowserPolicy() == DefaultBrowserPolicy.FIRST) {
      browserManager.firstActiveBrowser?.let {
        browse(url, it, project)
        return
      }
    }
    else if (SystemInfo.isMac && "open" == browserPath) {
      browserManager.firstActiveBrowser?.let {
        browseUsingPath(url, null, it, project)
        return
      }
    }

    super.openWithExplicitBrowser(url, browserPath, project)
  }

  override fun showError(@NlsContexts.DialogMessage error: String?, browser: WebBrowser?, project: Project?, @NlsContexts.DialogTitle title: String?, fix: (() -> Unit)?) {
    AppUIExecutor.onUiThread().expireWith(project ?: Disposable {}).submit {
      if (showOkNoDialog(title ?: IdeBundle.message("browser.error"), error ?: IdeBundle.message("unknown.error"), project,
                         okText = IdeBundle.message("button.fix"),
                         noText = Messages.getOkButton())) {
        val browserSettings = BrowserSettings()
        if (ShowSettingsUtil.getInstance().editConfigurable(project, browserSettings, browser?.let { Runnable { browserSettings.selectBrowser(it) } })) {
          fix?.invoke()
        }
      }
    }
  }
}