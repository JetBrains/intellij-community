// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl.ui

import com.intellij.execution.wsl.WSLDistribution
import com.intellij.ide.IdeBundle
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.NioFileChooserBuilder
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.platform.eel.impl.local.listWindowsLocalDriveRoots
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.ui.TextAccessor
import com.intellij.util.concurrency.annotations.RequiresEdt
import java.awt.Component
import java.nio.file.Path

/**
 * Creates "browse" dialog for WSL.
 *
 * [linuxPathField] field with wsl path
 *
 * User can choose either ``\\wsl$`` path for [distro] or Windows path (only if [accessWindowsFs] set).
 * [customFileDescriptor] adds additional filters, and accomplished with roots (according to [accessWindowsFs])
 */
@RequiresEdt
fun browseWslPath(
  linuxPathField: TextAccessor,
  distro: WSLDistribution,
  parent: Component,
  accessWindowsFs: Boolean = true,
  customFileDescriptor: FileChooserDescriptor? = null,
) {
  val descriptor = customFileDescriptor ?: FileChooserDescriptorFactory.multiFilesOrDirs()
  val roots = createWslRoots(distro, accessWindowsFs)
  if (roots.isEmpty()) {
    JBPopupFactory.getInstance().createMessage(IdeBundle.message("wsl.no_path")).show(parent)
    return
  }
  val linuxPathText = linuxPathField.text
  val toSelect = runWithModalProgressBlocking(ModalTaskOwner.component(parent), IdeBundle.message("wsl.path.browser.progress")) {
    findNearestExistingWindowsPath(distro, linuxPathText) ?: distro.getUNCRootPath()
  }
  val chosen = NioFileChooserBuilder(descriptor)
    .withPathRoots(roots)
    .chooseFiles(ProjectManager.getInstance().defaultProject, toSelect, parent)
  val linuxPath = chosen.firstOrNull()?.let { distro.getWslPath(it) } ?: return
  linuxPathField.text = linuxPath
}

/**
 * Returns the roots to show: the root of [distro], and the local drives when [accessWindowsFs] is `true`.
 * Returns an empty list when the WSL environment of [distro] is not available.
 */
private fun createWslRoots(distro: WSLDistribution, accessWindowsFs: Boolean): List<Path> {
  val distroRoot = distro.getUNCRootPath()
  if (distroRoot.asEelPath().descriptor is LocalEelDescriptor) return emptyList()
  return if (accessWindowsFs) listOf(distroRoot) + listWindowsLocalDriveRoots() else listOf(distroRoot)
}
