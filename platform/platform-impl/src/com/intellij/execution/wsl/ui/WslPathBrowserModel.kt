// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl.ui

import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.listWindowsLocalDriveRoots
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import java.io.File


/**
 * User enters [linuxPath] on [distro] and this function returns Windows to open in file browser.
 * It tries to match path partially if file doesn't exist. Returns null if [linuxPath] is not a linux path at all
 */
fun getBestWindowsPathFromLinuxPath(distro: WSLDistribution, linuxPath: String): VirtualFile? {
  val fs = LocalFileSystem.getInstance()
  var file: VirtualFile? = null

  @Suppress("NAME_SHADOWING")
  val linuxPath = linuxPath.trim()
  val logger = Logger.getInstance(WslPathBrowser::class.java)
  logger.info("Open $linuxPath in ${distro.getUNCRootPath()}${FileUtil.toSystemDependentName(FileUtil.normalize(linuxPath))}")
  distro.getWindowsPath(linuxPath).let {
    var fileName: String? = it
    while (file == null && fileName != null) {
      file = fs.findFileByPath(fileName)
      fileName = File(fileName).parent
    }
  }
  if (file == null) {
    logger.warn("Failed to find file $linuxPath")
  }
  return file
}

/**
 * Returns a set of roots to pass into [FileChooserDescriptor.withRoots] to initialize a descriptor.
 */
fun getRootsForFileDescriptor(distro: WSLDistribution, accessWindowsFs: Boolean): MutableList<VirtualFile> {
  val fs = LocalFileSystem.getInstance()
  val roots = mutableListOf<VirtualFile>()
  fs.findFileByNioFile(distro.getUNCRootPath())?.let { roots.add(it) }
  if (accessWindowsFs) {
    roots.addAll(listWindowsLocalDriveRoots().mapNotNull { fs.findFileByNioFile(it) })
  }
  return roots
}

/**
 * Creates [FileChooserDescriptor] for file browser that only allows access to [distro] FS and optionally [accessWindowsFs]
 */
fun createFileChooserDescriptor(distro: WSLDistribution, accessWindowsFs: Boolean): FileChooserDescriptor {
  val roots = getRootsForFileDescriptor(distro, accessWindowsFs)
  return FileChooserDescriptorFactory.createAllButJarContentsDescriptor().apply {
    withRoots(roots)
  }
}