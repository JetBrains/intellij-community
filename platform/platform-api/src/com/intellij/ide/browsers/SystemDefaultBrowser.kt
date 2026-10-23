// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.browsers

import com.intellij.execution.CommandLineUtil
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.execution.util.ExecUtil
import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.util.system.LowLevelLocalMachineAccess
import com.intellij.util.system.OS
import org.jetbrains.annotations.ApiStatus
import java.awt.Desktop
import java.util.UUID
import javax.swing.Icon

/**
 * The browser that the operating system opens for a URL.
 *
 * It opens a link with the desktop API, or with the default command of the operating system.
 * It has no family, no path and no settings.
 * It is not in the browser list of `WebBrowserManager`, but `WebBrowserManager.findBrowserById` finds it by its [id][getId].
 */
@ApiStatus.Internal
@OptIn(LowLevelLocalMachineAccess::class)
object SystemDefaultBrowser : WebBrowser() {
  private val LOG = logger<SystemDefaultBrowser>()

  private val ID: UUID = UUID.fromString("5d4b3c8e-0f6a-4e1d-9b27-6c8a1f3e2d90")

  override fun getName(): String = IdeBundle.message("settings.browsers.system.default")

  override fun getId(): UUID = ID

  override fun getFamily(): BrowserFamily? = null

  override fun getIcon(): Icon = AllIcons.General.Web

  override fun getPath(): String? = null

  override fun getBrowserNotFoundMessage(): String = IdeBundle.message("browser.default.not.supported")

  override fun getSpecificSettings(): BrowserSpecificSettings? = null

  override fun browse(url: String, project: Project?) {
    val uri = if (BrowserLauncherAppless.isDesktopActionSupported(Desktop.Action.BROWSE)) VfsUtil.toUri(url) else null
    if (uri == null) {
      openWithDefaultCommand(url, project)
      return
    }

    BrowserLauncherAppless.runInBackground(project) {
      try {
        LOG.debug("trying Desktop#browse")
        Desktop.getDesktop().browse(uri)
      }
      catch (e: Exception) {
        LOG.warn("[${uri}]", e)
        if (OS.CURRENT == OS.macOS && e.message!!.contains("Error code: -10814")) {
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
      BrowserLauncherAppless.showBrowserError(browserNotFoundMessage, url, browser = null, project)
      return
    }
    BrowserLauncherAppless.runBrowserCommand(GeneralCommandLine(command).withParameters(url), url, browser = null, project)
  }

  private val defaultCommand: List<String>?
    get() = when {
      OS.CURRENT == OS.Windows -> listOf(CommandLineUtil.getWinShellName(), "/c", "start", GeneralCommandLine.inescapableQuote(""))
      OS.CURRENT == OS.macOS -> listOf(ExecUtil.openCommandPath)
      PathEnvironmentVariableUtil.isOnPath("xdg-open") -> listOf("xdg-open")
      else -> null
    }

  override fun toString(): String = "SystemDefaultBrowser"
}
