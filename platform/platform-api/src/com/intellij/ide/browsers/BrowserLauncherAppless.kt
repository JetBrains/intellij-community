// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.browsers

import com.intellij.diagnostic.LoadingState
import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.execution.CommandLineUtil
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.util.ExecUtil
import com.intellij.ide.BrowserUtil
import com.intellij.ide.GeneralLocalSettings
import com.intellij.ide.IdeBundle
import com.intellij.ide.browsers.BrowserLauncherAppless.Companion.showBrowserError
import com.intellij.model.SideEffectGuard
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileTypes.systemOpenCommand
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts.NotificationContent
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.util.PathUtil
import com.intellij.util.io.URLUtil
import com.intellij.util.system.LowLevelLocalMachineAccess
import com.intellij.util.system.OS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import org.jetbrains.annotations.ApiStatus
import java.awt.Desktop
import java.io.IOException
import java.net.URI
import java.nio.file.Path

@ApiStatus.Internal
@OptIn(LowLevelLocalMachineAccess::class)
open class BrowserLauncherAppless : BrowserLauncher() {
  companion object {
    private val LOG = logger<BrowserLauncherAppless>()

    @JvmStatic
    fun canUseSystemDefaultBrowserPolicy(): Boolean =
      isDesktopActionSupported(Desktop.Action.BROWSE) || OS.CURRENT == OS.Windows || OS.CURRENT == OS.macOS || PathEnvironmentVariableUtil.isOnPath("xdg-open")

    private fun isDesktopActionSupported(action: Desktop.Action): Boolean =
      Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(action)

    /**
     * Starts [command], which opens [url] in [browser], in the background.
     * A failure goes to [showBrowserError]. A `null` [browser] means the system default browser.
     */
    private fun runBrowserCommand(command: GeneralCommandLine, url: String, browser: WebBrowser?, project: Project?) {
      LOG.debug { "starting [${command.commandLineString}]" }
      runInBackground(project) {
        try {
          val output = CapturingProcessHandler.Silent(command).runProcess(10000, false)
          if (!output.checkSuccess(LOG) && output.exitCode == 1) {
            @NlsSafe val error = output.stderrLines.firstOrNull()
            showBrowserError(error, url, browser, project)
          }
        }
        catch (e: ExecutionException) {
          LOG.warn(e)
          showBrowserError(e.message, url, browser, project)
        }
      }
    }

    /**
     * Reports that [url] did not open in [browser]. A `null` [browser] means the system default browser.
     * In the running IDE, the notification offers to fix the settings and to open the link again.
     * Before the components load, the launcher only logs the message.
     */
    private fun showBrowserError(message: @NotificationContent String?, url: String, browser: WebBrowser?, project: Project?) {
      // a link of the system default browser opens again with the default browser, because the developer can pick another one
      val launcher = currentLauncher()
      launcher.showError(message, project, browser) { launcher.browse(url, browser, project) }
    }

    /**
     * Opens [url] in the application that the operating system registers for its scheme.
     * Uses [systemOpenCommand] instead of the desktop API, which can select the web browser for any scheme.
     * On Windows, the command avoids shell expansion of environment variables in the URL.
     */
    @ApiStatus.Internal
    fun openWithSchemeHandler(url: String, project: Project?) {
      val command = systemOpenCommand(url)
      if (command == null) {
        showBrowserError(SystemDefaultBrowser.browserNotFoundMessage, url, browser = null, project)
        return
      }
      runBrowserCommand(GeneralCommandLine(command), url, browser = null, project)
    }

    internal fun runInBackground(project: Project?, action: suspend CoroutineScope.() -> Unit) {
      @Suppress("UsagesOfObsoleteApi")
      val scope = ((project ?: ApplicationManager.getApplication()) as? ComponentManagerEx)?.getCoroutineScope() ?: MainScope()
      (scope + Dispatchers.IO).launch(block = action)
    }

    private fun currentLauncher(): BrowserLauncherAppless =
      (if (LoadingState.COMPONENTS_LOADED.isOccurred) instance as? BrowserLauncherAppless else null) ?: BrowserLauncherAppless()
  }

  override fun open(url: String) {
    if (BrowserUtil.isAbsoluteURL(url)) {
      browse(url, browser = null, project = null)
    }
    else {
      browse(Path.of(url))
    }
  }

  override fun browse(file: Path) {
    if (isDesktopActionSupported(Desktop.Action.OPEN)) {
      openWithDesktopApi(file)
    }
    else {
      open(file)
    }
  }

  private fun openWithDesktopApi(file: Path) {
    runInBackground(null) {
      try {
        LOG.debug { "trying Desktop#open on [${file}]" }
        @Suppress("IO_FILE_USAGE")
        Desktop.getDesktop().open(file.toFile())
      }
      catch (e: IOException) {
        LOG.warn("[$file]", e)
        open(file)
      }
    }
  }

  private fun open(file: Path) {
    browse(file.toAbsolutePath().toUri().toString(), browser = null, project = null)
  }

  override fun browse(url: String, browser: WebBrowser?, project: Project?) {
    SideEffectGuard.checkSideEffectAllowed(SideEffectGuard.EffectType.EXEC)

    if (url.startsWith("jar:")) {
      LOG.info("ignoring 'jar:' URL")
      return
    }

    val signedUrl = signUrl(url.trim { it <= ' ' })
    LOG.debug { "opening [${signedUrl}]" }

    if (!canBrowse(project, signedUrl)) return

    val uri = VfsUtil.toUri(signedUrl)
    if (uri == null) {
      showError(IdeBundle.message("error.malformed.url", signedUrl), project)
      return
    }
    if (uri.scheme.equals(StandardFileSystems.FILE_PROTOCOL, ignoreCase = true) && uri.host != null) {
      showError(IdeBundle.message("error.unc.not.supported", uri), project)
      return
    }

    browse(uri, browser, project)
  }

  private fun browse(uri: URI, browser: WebBrowser?, project: Project?) {
    if (processWithUrlOpener(browser, uri.toString(), project)) {
      return
    }

    if (openMailToUrl(uri, project)) {
      return
    }

    val url = uri.toString()
    val effectiveBrowser = browser ?: getDefaultBrowser()
    if (effectiveBrowser != null) {
      openInBrowser(url, effectiveBrowser, project)
      return
    }

    // before the components load, the launcher knows only the settings of GeneralLocalSettings
    val settings = generalSettings
    if (settings.useDefaultBrowser) {
      openInSystemDefaultBrowser(url, project)
    }
    else {
      runBrowserCommand(GeneralCommandLine(BrowserUtil.getOpenBrowserCommand(settings.browserPath, url, emptyList(), false)), url, null, project)
    }
  }

  private fun openInBrowser(url: String, browser: WebBrowser, project: Project?) {
    when (browser) {
      SystemDefaultBrowser -> openInSystemDefaultBrowser(url, project)
      is EditorTabWebBrowser -> browser.browse(url, project)
      else -> {
        val browserPath = PathUtil.toSystemDependentName(browser.path)
        if (browserPath.isNullOrBlank()) {
          showBrowserError(browser.browserNotFoundMessage, url, browser, project)
          return
        }
        val settings = browser.specificSettings
        val parameters = settings?.additionalParameters ?: emptyList()
        val environment = settings?.environmentVariables ?: emptyMap()
        val command = GeneralCommandLine(BrowserUtil.getOpenBrowserCommand(browserPath, url, parameters, false))
          .withEnvironment(environment)
        runBrowserCommand(command, url, browser, project)
      }
    }
  }

  private fun openInSystemDefaultBrowser(url: String, project: Project?) {
    val uri = if (isDesktopActionSupported(Desktop.Action.BROWSE)) VfsUtil.toUri(url) else null
    if (uri == null) {
      openWithDefaultCommand(url, project)
      return
    }

    runInBackground(project) {
      try {
        LOG.debug("trying Desktop#browse")
        Desktop.getDesktop().browse(uri)
      }
      catch (e: Exception) {
        rethrowControlFlowException(e)
        LOG.warn("[${uri}]", e)
        if (OS.CURRENT == OS.macOS && e.message?.contains("Error code: -10814") == true) {
          // if "No application knows how to open" the URL, there is no sense in retrying with the 'open' command
          return@runInBackground
        }
        openWithDefaultCommand(url, project)
      }
    }
  }

  private fun openWithDefaultCommand(url: String, project: Project?) {
    val command = defaultCommand
    if (command == null) {
      showBrowserError(SystemDefaultBrowser.browserNotFoundMessage, url, browser = null, project)
      return
    }
    runBrowserCommand(GeneralCommandLine(command).withParameters(url), url, browser = null, project)
  }

  private val defaultCommand: List<String>?
    get() = when {
      OS.CURRENT == OS.Windows -> listOf(CommandLineUtil.getWinShellName(), "/c", "start", GeneralCommandLine.inescapableQuote(""))
      OS.CURRENT == OS.macOS -> listOf(ExecUtil.openCommandPath)
      PathEnvironmentVariableUtil.isOnPath("xdg-open") -> listOf("xdg-open")
      else -> null
    }

  private fun openMailToUrl(uri: URI, project: Project?): Boolean {
    if (uri.scheme == "mailto" && isDesktopActionSupported(Desktop.Action.MAIL)) {
      runInBackground(project) {
        try {
          LOG.debug("trying Desktop#mail")
          Desktop.getDesktop().mail(uri)
        }
        catch (e: Exception) {
          LOG.warn("[${uri}]", e)
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
      // `UrlOpener` does not get the system default browser
      if (effectiveBrowser != null && effectiveBrowser !== SystemDefaultBrowser) {
        val handled = UrlOpener.EP_NAME.extensionList.any {
          LOG.debug { "trying ${it.javaClass}" }
          it.openUrl(effectiveBrowser, url, project)
        }
        if (!handled) {
          openInBrowser(url, effectiveBrowser, project)
        }
        return true
      }
    }

    return false
  }

  protected open fun signUrl(url: String): String = url

  protected open fun getDefaultBrowser(): WebBrowser? = null

  protected open fun canBrowse(project: Project?, uri: String): Boolean = true

  protected open fun showError(message: @NotificationContent String?, project: Project?, browser: WebBrowser? = null, retry: (() -> Unit)? = null) {
    // the app is not started yet; unable to show a message
    LOG.warn(message)
  }

  private val generalSettings: GeneralLocalSettings
    get() = if (ApplicationManager.getApplication() != null) GeneralLocalSettings.getInstance() else GeneralLocalSettings()

}
