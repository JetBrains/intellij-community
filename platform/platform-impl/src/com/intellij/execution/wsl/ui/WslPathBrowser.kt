// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl.ui

import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.listWindowsLocalDriveRoots
import com.intellij.ide.IdeBundle
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.universal.UniversalFileChooser
import com.intellij.openapi.fileChooser.universal.UniversalFileChooserContributor
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.popup.JBPopupFactory
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
fun browseWslPath(linuxPathField: TextAccessor,
                  distro: WSLDistribution,
                  parent: Component,
                  accessWindowsFs: Boolean = true,
                  customFileDescriptor: FileChooserDescriptor? = null) {
  val windowsPath = getBestWindowsPathFromLinuxPath(distro, linuxPathField.text)
  if (windowsPath == null) {
    JBPopupFactory.getInstance().createMessage(IdeBundle.message("wsl.no_path")).show(parent)
  }

  val descriptor = customFileDescriptor ?: FileChooserDescriptorFactory.createAllButJarContentsDescriptor()
  val contributors = createWslContributors(distro, accessWindowsFs)
  val project = ProjectManager.getInstance().defaultProject
  val dialog = UniversalFileChooser.Dialog(
    project = project,
    parent = parent,
    descriptor = descriptor,
    contributors = contributors,
  )
  val files = if (windowsPath != null) dialog.choose(null, windowsPath) else dialog.choose(null)
  val linuxPath = files.firstOrNull()?.toNioPath()?.let { distro.getWslPath(it) } ?: return
  linuxPathField.text = linuxPath
}

private fun createWslContributors(distro: WSLDistribution, accessWindowsFs: Boolean): List<UniversalFileChooserContributor> {
  val allContributors = UniversalFileChooserContributor.EP_NAME.extensionList
  val result = mutableListOf<UniversalFileChooserContributor>()

  val distroRoot = distro.getUNCRootPath()
  val wslContributor = allContributors.firstOrNull { it.ownsPath(distroRoot) }
  if (wslContributor != null) {
    result.add(SingleWslDistroContributor(wslContributor, distroRoot))
  }

  if (accessWindowsFs) {
    val localDriveRoot = listWindowsLocalDriveRoots().firstOrNull()
    val localContributor = localDriveRoot?.let { drive -> allContributors.firstOrNull { it.ownsPath(drive) } }
    if (localContributor != null) {
      result.add(localContributor)
    }
  }
  return result
}

private class SingleWslDistroContributor(
  private val delegate: UniversalFileChooserContributor,
  private val distroRootPath: Path,
) : UniversalFileChooserContributor by delegate {
  override suspend fun getRoots(): List<UniversalFileChooserContributor.Root> = delegate.getFilteredRoots(distroRootPath)
}