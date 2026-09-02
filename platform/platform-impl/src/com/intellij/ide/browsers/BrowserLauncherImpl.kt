// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.browsers

import com.intellij.CommonBundle
import com.intellij.ide.IdeBundle
import com.intellij.ide.trustedProjects.TrustedFiles
import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.util.NlsContexts.NotificationContent
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Urls
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.ide.BuiltInServerManager
import java.nio.file.Path

@ApiStatus.Internal
open class BrowserLauncherImpl : BrowserLauncherAppless() {
  companion object {
    /**
     * Resolves [uri] to a local file whose trust the file itself decides
     * (see [TrustedFiles.isTrustDecidedByFile]).
     *
     * Returns `null` for a non-`file:` URI, a file missing from the VFS,
     * and a file that the project-level trust governs.
     */
    @VisibleForTesting
    internal fun findStandaloneFile(project: Project, uri: String): VirtualFile? {
      val javaUri = VfsUtil.toUri(uri) ?: return null
      if (!StandardFileSystems.FILE_PROTOCOL.equals(javaUri.scheme, ignoreCase = true)) return null
      // browse() itself rejects a UNC path right after this gate
      if (javaUri.host != null) return null
      val path = try {
        Path.of(javaUri)
      }
      catch (_: Exception) {
        return null
      }
      // no refresh: in this scenario the file is already open in an editor, hence in the VFS
      val file = LocalFileSystem.getInstance().findFileByNioFile(path) ?: return null
      return file.takeIf { TrustedFiles.isTrustDecidedByFile(it, project) }
    }
  }

  override fun getDefaultBrowser(): WebBrowser? {
    val browserManager = WebBrowserManager.getInstance()
    return if (browserManager.getDefaultBrowserPolicy() == DefaultBrowserPolicy.FIRST) browserManager.firstActiveBrowser else null
  }

  override fun canBrowse(project: Project?, uri: String): Boolean {
    if (project == null) {
      return true
    }
    val standaloneFile = findStandaloneFile(project, uri)
    if (standaloneFile != null) {
      // an externally opened file outside the project roots: the file trust model
      // replaces the project one, see UntrustedProjectNotificationProvider
      return TrustedFiles.isTrusted(standaloneFile, project) || confirmOpeningUntrustedFile(project, uri, standaloneFile)
    }
    if (TrustedProjects.isProjectTrusted(project)) {
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
      trustLabel -> { TrustedProjects.setProjectTrusted(project, true); return true }
      else -> return false
    }
  }

  private fun confirmOpeningUntrustedFile(project: Project, uri: String, file: VirtualFile): Boolean {
    val yesLabel = IdeBundle.message("external.link.confirmation.yes.label")
    val trustLabel = IdeBundle.message("external.link.confirmation.trust.file.label")
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
      trustLabel -> {
        // the same grant as TrustedProjectsDialog.confirmTrustingUntrustedFile: the trust event
        // resets TrustedFilesCache, and the open editor leaves the safe mode
        file.fileSystem.getNioPath(file)?.let { TrustedProjects.setProjectTrusted(it, true) }
        return true
      }
      else -> return false
    }
  }

  override fun signUrl(url: String): String {
    val parsedUrl = Urls.parse(url, false)
    if (parsedUrl != null) {
      val serverManager = BuiltInServerManager.getInstance()
      if (serverManager.isOnBuiltInWebServer(parsedUrl)) {
        return serverManager.addAuthToken(parsedUrl).toExternalForm()
      }
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
    val title = IdeBundle.message(if (retry != null) "notification.title.browser.config.problem" else "notification.title.cannot.open")
    val content = message ?: IdeBundle.message("unknown.error")
    Notification("BrowserCfgProblems", title, content, NotificationType.WARNING)
      .apply {
        if (retry != null) {
          addAction(NotificationAction.createSimpleExpiring(IdeBundle.message("button.fix")) {
            val browserSettings = BrowserSettings()
            val initializer = browser?.let { Runnable { browserSettings.selectBrowser(it) } }
            if (ShowSettingsUtil.getInstance().editConfigurable(project, browserSettings, initializer)) {
              retry.invoke()
            }
          })
        }
      }
      .notify(project)
  }
}
