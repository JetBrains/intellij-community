// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.browsers

import com.intellij.CommonBundle
import com.intellij.Patches
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessIOExecutorService
import com.intellij.execution.util.ExecUtil
import com.intellij.ide.BrowserUtil
import com.intellij.ide.GeneralLocalSettings
import com.intellij.ide.IdeBundle
import com.intellij.model.SideEffectGuard
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
import com.intellij.util.ui.GraphicsUtil
import java.awt.Desktop
import java.awt.GraphicsEnvironment
import java.io.File
import java.io.IOException
import java.net.URI
import java.nio.file.Path

open class BrowserLauncherAppless : BrowserLauncher() {
  companion object {
    private val LOG = logger<BrowserLauncherAppless>()

    @JvmStatic
    fun canUseSystemDefaultBrowserPolicy(): Boolean =
      isDesktopActionSupported(Desktop.Action.BROWSE) || SystemInfo.isMac || SystemInfo.isWindows || SystemInfo.hasXdgOpen()

    private fun isDesktopActionSupported(action: Desktop.Action): Boolean =
      !isAffectedByDesktopBug() && Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(action)

    private fun isAffectedByDesktopBug(): Boolean =
      Patches.SUN_BUG_ID_6486393 && (GraphicsEnvironment.isHeadless() || !GraphicsUtil.isRemoteEnvironment())
  }

  override fun open(url: String): Unit = openOrBrowse(url, false)

  override fun browse(file: File) {
    val path = file.absolutePath
    val absPath = if (SystemInfo.isWindows && path[0] != '/') "/${path}" else path
    openOrBrowse("${StandardFileSystems.FILE_PROTOCOL_PREFIX}${absPath}", true)
  }

  override fun browse(file: Path) {
    val path = file.toAbsolutePath().toString()
    val absPath = if (SystemInfo.isWindows && path[0] != '/') "/${path}" else path
    openOrBrowse("${StandardFileSystems.FILE_PROTOCOL_PREFIX}${absPath}", true)
  }

  protected open fun openWithExplicitBrowser(url: String, browserPath: String?, project: Project?) {
    browseUsingPath(url, browserPath, project = project)
  }

  protected open fun openOrBrowse(_url: String, browse: Boolean, project: Project? = null) {
    SideEffectGuard.checkSideEffectAllowed(SideEffectGuard.EffectType.EXEC)
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
    if (settings.useDefaultBrowser) {
      openWithDefaultBrowser(url, project)
    }
    else {
      openWithExplicitBrowser(url, settings.browserPath, project = project)
    }
  }

  private fun openWithDefaultBrowser(url: String, project: Project?) {
    if (desktopBrowse(project, url)) {
      return
    }
    systemOpen(project, url)
  }

  private fun desktopBrowse(project: Project?, url: String): Boolean {
    if (isDesktopActionSupported(Desktop.Action.BROWSE)) {
      val uri = VfsUtil.toUri(url)
      if (uri == null) {
        showError(IdeBundle.message("error.malformed.url", url), project = project)
        return true
      }
      return desktopBrowse(project, uri)
    }
    return false
  }

  protected open fun desktopBrowse(project: Project?, uri: URI): Boolean {
    try {
      LOG.debug("Trying Desktop#browse")
      Desktop.getDesktop().browse(uri)
      return true
    }
    catch (e: Exception) {
      LOG.warn("[$uri]", e)
      // if "No application knows how to open" the URL, there is no sense in retrying with 'open' command
      return SystemInfo.isMac && e.message!!.contains("Error code: -10814")
    }
  }

  private fun systemOpen(project: Project?, url: String) {
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
    SideEffectGuard.checkSideEffectAllowed(SideEffectGuard.EffectType.EXEC)
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
    SideEffectGuard.checkSideEffectAllowed(SideEffectGuard.EffectType.EXEC)
    if (url != null && url.startsWith("jar:")) return false

    val byName = browserPath == null && browser != null
    val effectivePath = if (byName) PathUtil.toSystemDependentName(browser!!.path) else browserPath
    val fix: (() -> Unit)? = if (byName) { -> browseUsingPath(url, null, browser!!, project, openInNewWindow, additionalParameters) } else null

    if (effectivePath.isNullOrBlank()) {
      val message = browser?.browserNotFoundMessage ?: IdeBundle.message("error.please.specify.path.to.web.browser", CommonBundle.settingsActionPath())
      showError(message, browser, project, IdeBundle.message("title.browser.not.found"), fix)
      return false
    }

    val browserSpecificSettings = browser?.specificSettings
    val parameters = (browserSpecificSettings?.additionalParameters ?: emptyList()) + additionalParameters
    val commandLine = GeneralCommandLine(BrowserUtil.getOpenBrowserCommand(effectivePath, url, parameters, openInNewWindow))
    if (browserSpecificSettings != null) {
      commandLine.environment.putAll(browserSpecificSettings.environmentVariables)
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

  protected open fun showError(@NlsContexts.DialogMessage error: String?,
                               browser: WebBrowser? = null,
                               project: Project? = null,
                               @NlsContexts.DialogTitle title: String? = null,
                               fix: (() -> Unit)? = null) {
    // not started yet; unable to show a message (may happen in the License panel on Linux)
    LOG.warn(error)
  }

  protected open fun getEffectiveBrowser(browser: WebBrowser?): WebBrowser? = browser

  private val generalSettings: GeneralLocalSettings
    get() = (if (ApplicationManager.getApplication() != null) GeneralLocalSettings.getInstance() else null) ?: GeneralLocalSettings()

  private val defaultBrowserCommand: List<String>?
    get() = when {
      SystemInfo.isWindows -> listOf(ExecUtil.windowsShellName, "/c", "start", GeneralCommandLine.inescapableQuote(""))
      SystemInfo.isMac -> listOf(ExecUtil.openCommandPath)
      SystemInfo.hasXdgOpen() -> listOf("xdg-open")
      else -> null
    }
}
