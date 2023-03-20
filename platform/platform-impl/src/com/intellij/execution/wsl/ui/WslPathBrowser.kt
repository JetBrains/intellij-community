// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl.ui

import com.intellij.execution.wsl.WSLDistribution
import com.intellij.ide.IdeBundle
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.ex.FileChooserDialogImpl
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.TextAccessor
import java.awt.Component
import javax.swing.SwingUtilities

/**
 * Creates "browse" dialog for WSL.
 * @param linuxPathField field with wsl path
 */
class WslPathBrowser(private val linuxPathField: TextAccessor) {

  /**
   * User can choose either ``\\wsl$`` path for [distro] or Windows path (only if [accessWindowsFs] set).
   * [customFileDescriptor] adds additional filters, and accomplished with roots (according to [accessWindowsFs])
   */
  fun browsePath(distro: WSLDistribution,
                 parent: Component,
                 accessWindowsFs: Boolean = true,
                 customFileDescriptor: FileChooserDescriptor? = null) {
    val windowsPath = ProgressManager.getInstance().runUnderProgress(IdeBundle.message("wsl.opening_wsl")) {
      getBestWindowsPathFromLinuxPath(distro, linuxPathField.text)
    }
    if (windowsPath == null) {
      JBPopupFactory.getInstance().createMessage(IdeBundle.message("wsl.no_path")).show(parent)
    }

    val roots = getRootsForFileDescriptor(distro, accessWindowsFs)
    val descriptor = (customFileDescriptor ?: FileChooserDescriptorFactory.createAllButJarContentsDescriptor()).apply {
      withRoots(roots)
    }
    val dialog = FileChooserDialogImpl(descriptor, parent)
    val files = if (windowsPath != null) dialog.choose(null, windowsPath) else dialog.choose(null)
    val linuxPath = files.firstOrNull()?.let { distro.getWslPath(it.path) } ?: return
    linuxPathField.text = linuxPath
  }
}

private fun <T> ProgressManager.runUnderProgress(@NlsContexts.DialogTitle title: String, code: () -> T): T =
  if (SwingUtilities.isEventDispatchThread()) {
    run(object : Task.WithResult<T, Exception>(null, title, false) {
      override fun compute(indicator: ProgressIndicator) = code()
    })
  }
  else {
    code()
  }
