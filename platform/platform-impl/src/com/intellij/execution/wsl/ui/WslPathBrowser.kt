// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl.ui

import com.intellij.execution.wsl.WSLDistribution
import com.intellij.ide.IdeBundle
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.ex.FileChooserDialogImpl
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.TextAccessor
import com.intellij.util.concurrency.annotations.RequiresEdt
import java.awt.Component

/**
 * Creates "browse" dialog for WSL.
 *
 * [linuxPathField] field with wsl path
 *
 * User can choose either ``\\wsl$`` path for [distro] or Windows path (only if [accessWindowsFs] set).
 * [customFileDescriptor] adds additional filters, and accomplished with roots (according to [accessWindowsFs])
 */
@RequiresEdt
fun browseWslPath(linuxPathField: TextAccessor,
                  distro: WSLDistribution,
                  parent: Component,
                  accessWindowsFs: Boolean = true,
                  customFileDescriptor: FileChooserDescriptor? = null) {
  val windowsPath = getBestWindowsPathFromLinuxPath(distro, linuxPathField.text)
  if (windowsPath == null) {
    JBPopupFactory.getInstance().createMessage(IdeBundle.message("wsl.no_path")).show(parent)
  }

  val roots = getRootsForFileDescriptor(distro, accessWindowsFs)
  val descriptor = (customFileDescriptor ?: FileChooserDescriptorFactory.createAllButJarContentsDescriptor()).apply {
    withRoots(roots)
  }
  val dialog = FileChooserDialogImpl(descriptor, parent)
  val files = if (windowsPath != null) dialog.choose(null, windowsPath) else dialog.choose(null)
  val linuxPath = files.firstOrNull()?.let { distro.getWslPath(it.toNioPath()) } ?: return
  linuxPathField.text = linuxPath
}