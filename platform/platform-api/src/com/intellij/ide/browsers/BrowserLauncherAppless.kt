// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.browsers

import com.intellij.CommonBundle
import com.intellij.Patches
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessIOExecutorService
import com.intellij.execution.util.ExecUtil
import com.intellij.ide.BrowserUtil
import com.intellij.ide.GeneralSettings
import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.util.PathUtil
import com.intellij.util.io.URLUtil
import java.awt.Desktop
import java.awt.GraphicsEnvironment
import java.io.File
import java.io.IOException
import java.net.URI
import java.nio.file.Path

private val LOG = logger<BrowserLauncherAppless>()

open class BrowserLauncherAppless : BrowserLauncher() {
  companion object {
    @JvmStatic
    fun canUseSystemDefaultBrowserPolicy(): Boolean =
      isDesktopActionSupported(Desktop.Action.BROWSE) || SystemInfo.isMac || SystemInfo.isWindows || SystemInfo.isUnix && SystemInfo.hasXdgOpen()

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

  override fun browse(file: Path) {
    var path = file.toAbsolutePath().toString()
    if (SystemInfo.isWindows && path[0] != '/') {
      path = "/$path"
    }
    openOrBrowse("${StandardFileSystems.FILE_PROTOCOL_PREFIX}$path", true)
  }

  protected open fun openWithExplicitBrowser(url: String, browserPath: String?, project: Project?) {
    browseUsingPath(url, browserPath, project = project)
  }

  protected open fun openOrBrowse(_url: String, browse: Boolean, project: Project? = null) {
    val url = signUrl(_url.trim { it <= ' ' })
    LOG.debug { "opening [$url]" }

    if (url.startsWith("mailto:") && isDesktopActionSupported(Desktop.Action.MAIL)) {
      try {
        LOG.debug("Trying Desktop#mail")
        Desktop.getDesktop().mail(URI(url))
      }
      catch (e: Exception) {
        LOG.warn("[$url]", e)
      }
      return
    }

    if (!BrowserUtil.isAbsoluteURL(url)) {
      val file = File(url)
      if (!browse && isDesktopActionSupported(Desktop.Action.OPEN)) {
        if (!file.exists()) {
          showError(IdeBundle.message("error.file.does.not.exist", file.path), project = project)
          return
        }

        try {
          LOG.debug("Trying Desktop#open")
          Desktop.getDesktop().open(file)
          return
        }
        catch (e: IOException) {
          LOG.warn("[$url]", e)
        }
      }

      browse(file)
      return
    }

    val settings = generalSettings
    if (settings.isUseDefaultBrowser) {
      openWithDefaultBrowser(url, project)
    }
    else {
      openWithExplicitBrowser(url, settings.browserPath, project = project)
    }
  }

  private fun openWithDefaultBrowser(url: String, project: Project?) {
    if (isDesktopActionSupported(Desktop.Action.BROWSE)) {
      val uri = VfsUtil.toUri(url)
      if (uri == null) {
        showError(IdeBundle.message("error.malformed.url", url), project = project)
        return
      }

      try {
        LOG.debug("Trying Desktop#browse")
        Desktop.getDesktop().browse(uri)
        return
      }
      catch (e: Exception) {
        LOG.warn("[$url]", e)
        if (SystemInfo.isMac && e.message!!.contains("Error code: -10814")) {
          return  // if "No application knows how to open" the URL, there is no sense in retrying with 'open' command
        }
      }
    }

    val command = defaultBrowserCommand
    if (command == null) {
      showError(IdeBundle.message("browser.default.not.supported"), project = project)
      return
    }

    if (url.startsWith("jar:")) return

    doLaunch(GeneralCommandLine(command).withParameters(url), project)
  }

  protected open fun signUrl(url: String): String = url

  override fun browse(url: String, browser: WebBrowser?, project: Project?) {
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
    if (url != null && url.startsWith("jar:")) return false

    val byName = browserPath == null && browser != null
    val effectivePath = if (byName) PathUtil.toSystemDependentName(browser!!.path) else browserPath
    val fix: (() -> Unit)? = if (byName) { -> browseUsingPath(url, null, browser!!, project, openInNewWindow, additionalParameters) } else null

    if (effectivePath.isNullOrBlank()) {
      val message = browser?.browserNotFoundMessage ?: IdeBundle.message("error.please.specify.path.to.web.browser", CommonBundle.settingsActionPath())
      showError(message, browser, project, IdeBundle.message("title.browser.not.found"), fix)
      return false
    }

    val commandWithUrl = BrowserUtil.getOpenBrowserCommand(effectivePath, openInNewWindow).toMutableList()
    if (url != null) {
      if (browser != null) browser.addOpenUrlParameter(commandWithUrl, url)
      else commandWithUrl += url
    }

    val commandLine = GeneralCommandLine(commandWithUrl)

    val browserSpecificSettings = browser?.specificSettings
    if (browserSpecificSettings != null) {
      commandLine.environment.putAll(browserSpecificSettings.environmentVariables)
    }

    val specific = browserSpecificSettings?.additionalParameters ?: emptyList()
    if (specific.size + additionalParameters.size > 0) {
      if (isOpenCommandUsed(commandLine)) {
        commandLine.addParameter("--args")
      }
      commandLine.addParameters(specific)
      commandLine.addParameters(*additionalParameters)
    }

    doLaunch(commandLine, project, browser, fix)
    return true
  }

  private fun doLaunch(command: GeneralCommandLine, project: Project?, browser: WebBrowser? = null, fix: (() -> Unit)? = null) {
    LOG.debug { command.commandLineString }
    ProcessIOExecutorService.INSTANCE.execute {
      try {
        val output = CapturingProcessHandler.Silent(command).runProcess(10000, false)
        if (!output.checkSuccess(LOG) && output.exitCode == 1) {
          @NlsSafe
          val error = output.stderrLines.firstOrNull()
          showError(error, browser, project, null, fix)
        }
      }
      catch (e: ExecutionException) {
        showError(e.message, browser, project, null, fix)
      }
    }
  }

  protected open fun showError(@NlsContexts.DialogMessage error: String?, browser: WebBrowser? = null, project: Project? = null, @NlsContexts.DialogTitle title: String? = null, fix: (() -> Unit)? = null) {
    // Not started yet. Not able to show message up. (Could happen in License panel under Linux).
    LOG.warn(error)
  }

  protected open fun getEffectiveBrowser(browser: WebBrowser?): WebBrowser? = browser
}

// AWT-replacing Projector is not affected by that bug, detect it by GE class name to avoid direct dependency on Projector
private fun isAffectedByDesktopBug(): Boolean =
  Patches.SUN_BUG_ID_6486393 && (GraphicsEnvironment.isHeadless() || "PGraphicsEnvironment" != GraphicsEnvironment.getLocalGraphicsEnvironment()?.javaClass?.simpleName)

private fun isDesktopActionSupported(action: Desktop.Action): Boolean =
  !isAffectedByDesktopBug() && Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(action)

private val generalSettings: GeneralSettings
  get() = (if (ApplicationManager.getApplication() != null) GeneralSettings.getInstance() else null) ?: GeneralSettings()

private val defaultBrowserCommand: List<String>?
  get() = when {
    SystemInfo.isWindows -> listOf(ExecUtil.windowsShellName, "/c", "start", GeneralCommandLine.inescapableQuote(""))
    SystemInfo.isMac -> listOf(ExecUtil.openCommandPath)
    SystemInfo.isUnix && SystemInfo.hasXdgOpen() -> listOf("xdg-open")
    else -> null
  }