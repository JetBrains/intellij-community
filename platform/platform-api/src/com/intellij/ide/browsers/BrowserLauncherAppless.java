/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.util.ArrayUtil
import com.intellij.util.PathUtil
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
          val settings = GeneralSettings.getInstance()
          if (settings != null) {
            return settings
          }
        }

        return GeneralSettings()
      }

    private val defaultBrowserCommand: List<String>?
      get() {
        if (SystemInfo.isWindows) {
          return Arrays.asList(ExecUtil.getWindowsShellName(), "/c", "start", GeneralCommandLine.inescapableQuote(""))
        }
        else if (SystemInfo.isMac) {
          return listOf(ExecUtil.getOpenCommandPath())
        }
        else if (SystemInfo.isUnix && SystemInfo.hasXdgOpen()) {
          return listOf("xdg-open")
        }
        else {
          return null
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

    fun isOpenCommandUsed(command: GeneralCommandLine): Boolean {
      return SystemInfo.isMac && ExecUtil.getOpenCommandPath() == command.exePath
    }
  }

  override fun open(url: String) = openOrBrowse(url, false, null)

  override fun browse(file: File) = browse(VfsUtil.toUri(file))

  override fun browse(uri: URI) = browse(uri, null)

  fun browse(uri: URI, project: Project?) {
    LOG.debug("Launch browser: [$uri]")

    val settings = generalSettings
    if (settings.isUseDefaultBrowser) {
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
        val command = defaultBrowserCommand
        if (command != null) {
          doLaunch(uri.toString(), command, null, project, ArrayUtil.EMPTY_STRING_ARRAY, null)
          return
        }
      }
    }

    browseUsingNotSystemDefaultBrowserPolicy(uri, settings, project)
  }

  protected open fun browseUsingNotSystemDefaultBrowserPolicy(uri: URI, settings: GeneralSettings, project: Project?) {
    browseUsingPath(uri.toString(), settings.browserPath, null, project, ArrayUtil.EMPTY_STRING_ARRAY)
  }

  private fun openOrBrowse(_url: String, browse: Boolean, project: Project?) {
    var url = _url.trim { it <= ' ' }

    val uri: URI?
    if (BrowserUtil.isAbsoluteURL(url)) {
      uri = VfsUtil.toUri(url)
    }
    else {
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

    if (uri == null) {
      showError(IdeBundle.message("error.malformed.url", url), null, project, null, null)
    }
    else {
      browse(uri, project)
    }
  }

  override fun browse(url: String, browser: WebBrowser?, project: Project?) {
    if (browser == null) {
      openOrBrowse(url, true, project)
    }
    else {
      for (urlOpener in UrlOpener.EP_NAME.extensions) {
        if (urlOpener.openUrl(browser, url, project)) {
          return
        }
      }
    }
  }

  override fun browseUsingPath(url: String?,
                               browserPath: String?,
                               browser: WebBrowser?,
                               project: Project?,
                               additionalParameters: Array<String>): Boolean {
    var browserPathEffective = browserPath
    var launchTask: (() -> Unit)? = null
    if (browserPath == null && browser != null) {
      browserPathEffective = PathUtil.toSystemDependentName(browser.path)
      launchTask = { browseUsingPath(url, null, browser, project, additionalParameters) }
    }
    return doLaunch(url, browserPathEffective, browser, project, additionalParameters, launchTask)
  }

  private fun doLaunch(url: String?,
                       browserPath: String?,
                       browser: WebBrowser?,
                       project: Project?,
                       additionalParameters: Array<String>,
                       launchTask: (() -> Unit)?): Boolean {
    if (!checkPath(browserPath, browser, project, launchTask)) {
      return false
    }
    return doLaunch(url, BrowserUtil.getOpenBrowserCommand(browserPath!!, false), browser, project, additionalParameters, launchTask)
  }

  @Contract("null, _, _, _ -> false")
  fun checkPath(browserPath: String?, browser: WebBrowser?, project: Project?, launchTask: (() -> Unit)?): Boolean {
    if (!StringUtil.isEmptyOrSpaces(browserPath)) {
      return true
    }

    val message = browser?.browserNotFoundMessage ?: IdeBundle.message("error.please.specify.path.to.web.browser", CommonBundle.settingsActionPath())
    showError(message, browser, project, IdeBundle.message("title.browser.not.found"), launchTask)
    return false
  }

  private fun doLaunch(url: String?,
                       command: List<String>,
                       browser: WebBrowser?,
                       project: Project?,
                       additionalParameters: Array<String>,
                       launchTask: (() -> Unit)?): Boolean {
    val commandLine = GeneralCommandLine(command)

    if (url != null && url.startsWith("jar:")) {
      return false
    }

    if (url != null) {
      commandLine.addParameter(url)
    }

    val browserSpecificSettings = browser?.specificSettings
    if (browserSpecificSettings != null) {
      commandLine.environment.putAll(browserSpecificSettings.environmentVariables)
    }

    addArgs(commandLine, browserSpecificSettings, additionalParameters)

    try {
      val process = commandLine.createProcess()
      checkCreatedProcess(browser, project, commandLine, process, launchTask)
      return true
    }
    catch (e: ExecutionException) {
      showError(e.message, browser, project, null, null)
      return false
    }

  }

  protected open fun checkCreatedProcess(browser: WebBrowser?,
                                         project: Project?,
                                         commandLine: GeneralCommandLine,
                                         process: Process,
                                         launchTask: (() -> Unit)?) {
  }

  protected open fun showError(error: String?, browser: WebBrowser?, project: Project?, title: String?, launchTask: (() -> Unit)?) {
    // Not started yet. Not able to show message up. (Could happen in License panel under Linux).
    LOG.warn(error)
  }
}