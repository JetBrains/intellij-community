// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.browsers

import com.intellij.CommonBundle
import com.intellij.Patches
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil
import com.intellij.ide.BrowserUtil
import com.intellij.ide.GeneralSettings
import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.util.ArrayUtil
import com.intellij.util.PathUtil
import com.intellij.util.io.URLUtil
import org.jetbrains.annotations.Contract
import java.awt.Desktop
import java.io.File
import java.io.IOException
import java.net.URI
import java.util.*

open class BrowserLauncherAppless : BrowserLauncher() {
  companion object {
    internal val LOG = Logger.getInstance(BrowserLauncherAppless::class.java)

    private fun isDesktopActionSupported(action: Desktop.Action): Boolean {
      return !Patches.SUN_BUG_ID_6486393 && Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(action)
    }

    @JvmStatic
    fun canUseSystemDefaultBrowserPolicy(): Boolean {
      return isDesktopActionSupported(Desktop.Action.BROWSE) ||
             SystemInfo.isMac || SystemInfo.isWindows ||
             SystemInfo.isUnix && SystemInfo.hasXdgOpen()
    }

    private val generalSettings: GeneralSettings
      get() {
        if (ApplicationManager.getApplication() != null) {
          GeneralSettings.getInstance()?.let {
            return it
          }
        }

        return GeneralSettings()
      }

    private val defaultBrowserCommand: List<String>?
      get() {
        return when {
          SystemInfo.isWindows -> listOf(ExecUtil.windowsShellName, "/c", "start", GeneralCommandLine.inescapableQuote(""))
          SystemInfo.isMac -> listOf(ExecUtil.openCommandPath)
          SystemInfo.isUnix && SystemInfo.hasXdgOpen() -> listOf("xdg-open")
          else -> null
        }
      }

    private fun addArgs(command: GeneralCommandLine, settings: BrowserSpecificSettings?, additional: Array<String>) {
      val specific = settings?.additionalParameters ?: emptyList<String>()
      if (specific.size + additional.size > 0) {
        if (isOpenCommandUsed(command)) {
          if (BrowserUtil.isOpenCommandSupportArgs()) {
            command.addParameter("--args")
          }
          else {
            LOG.warn("'open' command doesn't allow to pass command line arguments so they will be ignored: " +
                     StringUtil.join(specific, ", ") + " " + Arrays.toString(additional))
            return
          }
        }

        command.addParameters(specific)
        command.addParameters(*additional)
      }
    }

    fun isOpenCommandUsed(command: GeneralCommandLine): Boolean = SystemInfo.isMac && ExecUtil.openCommandPath == command.exePath
  }

  override fun open(url: String): Unit = openOrBrowse(url, false)

  override fun browse(file: File) {
    var path = file.absolutePath
    if (SystemInfo.isWindows && path[0] != '/') {
      path = "/$path"
    }
    openOrBrowse("${StandardFileSystems.FILE_PROTOCOL_PREFIX}$path", true)
  }

  protected open fun browseUsingNotSystemDefaultBrowserPolicy(url: String, settings: GeneralSettings, project: Project?) {
    browseUsingPath(url, settings.browserPath, project = project)
  }

  private fun openOrBrowse(_url: String, browse: Boolean, project: Project? = null) {
    val url = signUrl(_url.trim { it <= ' ' })

    if (url.startsWith("mailto:") && Desktop.getDesktop().isSupported(Desktop.Action.MAIL)) {
      try {
        Desktop.getDesktop().mail(URI(url))
      }
      catch (e: Exception) {
        LOG.warn("failed to open: $url", e)
      }
      return
    }

    if (!BrowserUtil.isAbsoluteURL(url)) {
      val file = File(url)
      if (!browse && isDesktopActionSupported(Desktop.Action.OPEN)) {
        if (!file.exists()) {
          showError(IdeBundle.message("error.file.does.not.exist", file.path), null, null, null, null)
          return
        }

        try {
          Desktop.getDesktop().open(file)
          return
        }
        catch (e: IOException) {
          LOG.debug(e)
        }
      }

      browse(file)
      return
    }

    LOG.debug("Launch browser: [$url]")
    val settings = generalSettings
    if (settings.isUseDefaultBrowser) {
      val uri = VfsUtil.toUri(url)
      if (uri == null) {
        showError(IdeBundle.message("error.malformed.url", url), project = project)
        return
      }

      var tryToUseCli = true
      if (isDesktopActionSupported(Desktop.Action.BROWSE)) {
        try {
          Desktop.getDesktop().browse(uri)
          LOG.debug("Browser launched using JDK 1.6 API")
          return
        }
        catch (e: Exception) {
          LOG.warn("Error while using Desktop API, fallback to CLI", e)
          // if "No application knows how to open", then we must not try to use OS open
          tryToUseCli = !e.message!!.contains("Error code: -10814")
        }
      }

      if (tryToUseCli) {
        defaultBrowserCommand?.let {
          doLaunch(url, it, null, project)
          return
        }
      }
    }

    browseUsingNotSystemDefaultBrowserPolicy(url, settings, project = project)
  }

  protected open fun signUrl(url: String): String = url

  final override fun browse(url: String, browser: WebBrowser?, project: Project?) {
    val effectiveBrowser = getEffectiveBrowser(browser)
    // if browser is not passed, UrlOpener should be not used for non-http(s) urls
    if (effectiveBrowser == null || (browser == null && !url.startsWith(URLUtil.HTTP_PROTOCOL))) {
      openOrBrowse(url, true, project)
    }
    else {
      UrlOpener.EP_NAME.extensions.any { it.openUrl(effectiveBrowser, signUrl(url), project) }
    }
  }

  override fun browseUsingPath(url: String?,
                               browserPath: String?,
                               browser: WebBrowser?,
                               project: Project?,
                               openInNewWindow: Boolean,
                               additionalParameters: Array<String>): Boolean {
    var browserPathEffective = browserPath
    var launchTask: (() -> Unit)? = null
    if (browserPath == null && browser != null) {
      browserPathEffective = PathUtil.toSystemDependentName(browser.path)
      launchTask = { browseUsingPath(url, null, browser, project, openInNewWindow, additionalParameters) }
    }
    return doLaunch(url, browserPathEffective, browser, project, openInNewWindow, additionalParameters, launchTask)
  }

  private fun doLaunch(url: String?,
                       browserPath: String?,
                       browser: WebBrowser?,
                       project: Project?,
                       openInNewWindow: Boolean,
                       additionalParameters: Array<String>,
                       launchTask: (() -> Unit)?): Boolean {
    if (!checkPath(browserPath, browser, project, launchTask)) {
      return false
    }
    return doLaunch(url, BrowserUtil.getOpenBrowserCommand(browserPath!!, openInNewWindow), browser, project, additionalParameters,
                    launchTask)
  }

  @Contract("null, _, _, _ -> false")
  fun checkPath(browserPath: String?, browser: WebBrowser?, project: Project?, launchTask: (() -> Unit)?): Boolean {
    if (!browserPath.isNullOrBlank()) {
      return true
    }

    val message = browser?.browserNotFoundMessage ?: IdeBundle.message("error.please.specify.path.to.web.browser", CommonBundle.settingsActionPath())
    showError(message, browser, project, IdeBundle.message("title.browser.not.found"), launchTask)
    return false
  }

  private fun doLaunch(url: String?, command: List<String>, browser: WebBrowser?, project: Project?, additionalParameters: Array<String> = ArrayUtil.EMPTY_STRING_ARRAY, launchTask: (() -> Unit)? = null): Boolean {
    val commandLine = GeneralCommandLine(command)

    if (url != null) {
      if (url.startsWith("jar:")) {
        return false
      }
      commandLine.addParameter(url)
    }

    val browserSpecificSettings = browser?.specificSettings
    if (browserSpecificSettings != null) {
      commandLine.environment.putAll(browserSpecificSettings.environmentVariables)
    }

    addArgs(commandLine, browserSpecificSettings, additionalParameters)

    return try {
      checkCreatedProcess(browser, project, commandLine, commandLine.createProcess(), launchTask)
      true
    }
    catch (e: ExecutionException) {
      showError(e.message, browser, project, null, null)
      false
    }

  }

  protected open fun checkCreatedProcess(browser: WebBrowser?, project: Project?, commandLine: GeneralCommandLine, process: Process, launchTask: (() -> Unit)?) {
  }

  protected open fun showError(error: String?, browser: WebBrowser? = null, project: Project? = null, title: String? = null, launchTask: (() -> Unit)? = null) {
    // Not started yet. Not able to show message up. (Could happen in License panel under Linux).
    LOG.warn(error)
  }

  protected open fun getEffectiveBrowser(browser: WebBrowser?): WebBrowser? = browser
}