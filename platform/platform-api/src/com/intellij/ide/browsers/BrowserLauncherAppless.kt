// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.browsers

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.util.ExecUtil
import com.intellij.ide.BrowserUtil
import com.intellij.ide.GeneralLocalSettings
import com.intellij.ide.IdeBundle
import com.intellij.model.SideEffectGuard
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts.NotificationContent
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.util.PathUtil
import com.intellij.util.io.URLUtil
import kotlinx.coroutines.*
import java.awt.Desktop
import java.io.File
import java.io.IOException
import java.net.URI
import java.nio.file.Path

open class BrowserLauncherAppless : BrowserLauncher() {
  companion object {
    private val LOG = logger<BrowserLauncherAppless>()

    @JvmStatic
    fun canUseSystemDefaultBrowserPolicy(): Boolean {
      return isDesktopActionSupported(Desktop.Action.BROWSE) || SystemInfo.isMac || SystemInfo.isWindows || SystemInfo.hasXdgOpen()
    }

    private fun isDesktopActionSupported(action: Desktop.Action): Boolean {
      return Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(action)
    }
  }

  override fun open(url: String) {
    if (BrowserUtil.isAbsoluteURL(url)) {
      browse(url, browser = null, project = null)
    }
    else {
      val file = File(url)
      if (isDesktopActionSupported(Desktop.Action.OPEN)) {
        if (!file.exists()) {
          showError(IdeBundle.message("error.file.does.not.exist", file.path))
          return
        }
        openWithDesktopApi(url, file)
      }
      else {
        browse(file)
      }
    }
  }

  private fun BrowserLauncherAppless.openWithDesktopApi(url: String, file: File) {
    getScope(null).launch {
      try {
        LOG.debug { "trying Desktop#open on [${url}]" }
        Desktop.getDesktop().open(file)
      }
      catch (e: IOException) {
        LOG.warn("[$url]", e)
        browse(file)
      }
    }
  }

  override fun browse(file: File) {
    val path = file.absolutePath
    val absPath = if (SystemInfo.isWindows && path[0] != '/') "/${path}" else path
    browse("${StandardFileSystems.FILE_PROTOCOL_PREFIX}${absPath}", browser = null, project = null)
  }

  override fun browse(file: Path) {
    val path = file.toAbsolutePath().toString()
    val absPath = if (SystemInfo.isWindows && path[0] != '/') "/${path}" else path
    browse("${StandardFileSystems.FILE_PROTOCOL_PREFIX}${absPath}", browser = null, project = null)
  }

  override fun browse(url: String, browser: WebBrowser?, project: Project?) {
    SideEffectGuard.checkSideEffectAllowed(SideEffectGuard.EffectType.EXEC)

    if (url.startsWith("jar:")) {
      LOG.info("ignoring 'jar:' URL")
      return
    }

    val signedUrl = signUrl(url.trim { it <= ' ' })
    LOG.debug { "opening [$signedUrl]" }

    if (processWithUrlOpener(browser, signedUrl, project)) {
      return
    }

    if (processMailToUrl(signedUrl, project)) {
      return
    }

    val settings = generalSettings
    if (settings.useDefaultBrowser) {
      if (!canBrowse(project, signedUrl)) {
        return
      }
      if (isDesktopActionSupported(Desktop.Action.BROWSE)) {
        val uri = VfsUtil.toUri(signedUrl)
        if (uri == null) {
          showError(IdeBundle.message("error.malformed.url", signedUrl), project)
          return
        }
        openWithDesktopApi(uri, project)
      }
      else {
        openWithDefaultBrowserCommand(signedUrl, project)
      }
    }
    else {
      val browserPath = settings.browserPath
      val substitutedBrowser = substituteBrowser(browserPath)
      if (substitutedBrowser != null) {
        openWithBrowser(signedUrl, substitutedBrowser, project)
      }
      else {
        spawn(GeneralCommandLine(BrowserUtil.getOpenBrowserCommand(browserPath, signedUrl, emptyList(), false)), project)
      }
    }
  }

  private fun processMailToUrl(url: String, project: Project?): Boolean {
    if (url.startsWith("mailto:") && isDesktopActionSupported(Desktop.Action.MAIL)) {
      getScope(project).launch {
        try {
          LOG.debug("trying Desktop#mail")
          Desktop.getDesktop().mail(URI(url))
        }
        catch (e: Exception) {
          LOG.warn("[${url}]", e)
        }
      }
      return true
    }

    return false
  }

  private fun processWithUrlOpener(browser: WebBrowser?, url: String, project: Project?): Boolean {
    if (browser != null || url.startsWith(URLUtil.HTTP_PROTOCOL)) {
      // if a browser is not specified, `UrlOpener` should not be used for non-HTTP(S) URLs
      val effectiveBrowser = browser ?: getDefaultBrowser()
      if (effectiveBrowser != null) {
        val handled = UrlOpener.EP_NAME.extensionList.any {
          LOG.debug { "trying ${it.javaClass}" }
          it.openUrl(effectiveBrowser, url, project)
        }
        if (!handled) {
          openWithBrowser(url, effectiveBrowser, project)
        }
        return true
      }
    }

    return false
  }

  private fun openWithDesktopApi(uri: URI, project: Project?) {
    getScope(project).launch {
      try {
        LOG.debug("trying Desktop#browse")
        Desktop.getDesktop().browse(uri)
      }
      catch (e: Exception) {
        LOG.warn("[${uri}]", e)
        if (SystemInfo.isMac && e.message!!.contains("Error code: -10814")) {
          // if "No application knows how to open" the URL, there is no sense in retrying with 'open' command
          return@launch
        }
        openWithDefaultBrowserCommand(uri.toString(), project)
      }
    }
  }

  private fun openWithDefaultBrowserCommand(url: String, project: Project?) {
    val command = defaultBrowserCommand
    if (command == null) {
      showError(IdeBundle.message("browser.default.not.supported"), project)
      return
    }

    spawn(GeneralCommandLine(command).withParameters(url), project)
  }

  private fun openWithBrowser(url: String, browser: WebBrowser, project: Project?) {
    val retry = { openWithBrowser(url, browser, project) }

    val browserPath = PathUtil.toSystemDependentName(browser.path)
    if (browserPath.isNullOrBlank()) {
      showError(browser.browserNotFoundMessage, project, browser, retry)
      return
    }

    val parameters = browser.specificSettings?.additionalParameters ?: emptyList()
    val environment = browser.specificSettings?.environmentVariables ?: emptyMap()
    val command = GeneralCommandLine(BrowserUtil.getOpenBrowserCommand(browserPath, url, parameters, false)).withEnvironment(environment)
    spawn(command, project, browser, retry)
  }

  private fun spawn(command: GeneralCommandLine, project: Project?, browser: WebBrowser? = null, retry: (() -> Unit)? = null) {
    LOG.debug { "starting [${command.commandLineString}]" }
    getScope(project).launch {
      try {
        val output = CapturingProcessHandler.Silent(command).runProcess(10000, false)
        if (!output.checkSuccess(LOG) && output.exitCode == 1) {
          @NlsSafe
          val error = output.stderrLines.firstOrNull()
          showError(error, project, browser, retry)
        }
      }
      catch (e: ExecutionException) {
        LOG.warn(e)
        showError(e.message, project, browser, retry)
      }
    }
  }

  protected open fun signUrl(url: String): String = url

  protected open fun getDefaultBrowser(): WebBrowser? = null

  protected open fun canBrowse(project: Project?, uri: String): Boolean = true

  protected open fun substituteBrowser(browserPath: String): WebBrowser? = null

  protected open fun showError(message: @NotificationContent String?,
                               project: Project? = null,
                               browser: WebBrowser? = null,
                               retry: (() -> Unit)? = null) {
    // not started yet; unable to show a message (may happen in the License panel on Linux)
    LOG.warn(message)
  }

  private val generalSettings: GeneralLocalSettings
    get() = if (ApplicationManager.getApplication() != null) GeneralLocalSettings.getInstance() else GeneralLocalSettings()

  private val defaultBrowserCommand: List<String>?
    get() = when {
      SystemInfo.isWindows -> listOf(ExecUtil.windowsShellName, "/c", "start", GeneralCommandLine.inescapableQuote(""))
      SystemInfo.isMac -> listOf(ExecUtil.openCommandPath)
      SystemInfo.hasXdgOpen() -> listOf("xdg-open")
      else -> null
    }

  private fun getScope(project: Project?): CoroutineScope {
    return (((project ?: ApplicationManager.getApplication()) as? ComponentManagerEx)?.getCoroutineScope() ?: MainScope()) + Dispatchers.IO
  }
}
