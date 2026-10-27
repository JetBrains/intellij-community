// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileTypes

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.icons.AllIcons
import com.intellij.ide.IdeCoreBundle
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.system.OS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.annotations.Nls
import java.awt.Desktop
import javax.swing.Icon

private val LOG = logger<NativeFileType>()

object NativeFileType : INativeFileType {
  @get:JvmSynthetic
  val INSTANCE: NativeFileType
    get() = this

  @JvmStatic
  fun openAssociatedApplication(file: VirtualFile): Boolean {
    require(file.isInLocalFileSystem) { "Non-local file: $file; FS=${file.fileSystem}" }

    // `Desktop.open` and the process start are native calls that can block for a long time
    // (e.g., `ProcessImpl.create` on Windows is synchronized), so they must not run on EDT
    if (Desktop.isDesktopSupported()) {
      val desktop = Desktop.getDesktop()
      if (desktop.isSupported(Desktop.Action.OPEN)) {
        coroutineScope().launch(Dispatchers.IO) {
          try {
            @Suppress("IO_FILE_USAGE")
            desktop.open(file.toNioPath().toFile())
          }
          catch (e: Exception) {
            rethrowControlFlowException(e)
            LOG.warn(e)
            getFallbackCommand(file)?.let(::startProcess)
          }
        }
        return true
      }
    }

    val command = getFallbackCommand(file) ?: return false
    coroutineScope().launch(Dispatchers.IO) {
      startProcess(command)
    }
    return true
  }

  override fun getName(): String = "Native"

  override fun getDescription(): String = IdeCoreBundle.message("filetype.native.description")

  override fun getDisplayName(): @Nls String = IdeCoreBundle.message("filetype.native.display.name")

  override fun getDefaultExtension(): String = ""

  override fun getIcon(): Icon = AllIcons.FileTypes.Custom

  override fun isBinary(): Boolean = true

  override fun openFileInAssociatedApplication(project: Project, file: VirtualFile): Boolean = openAssociatedApplication(file)

  override fun useNativeIcon(): Boolean = true
}

private fun coroutineScope(): CoroutineScope = service<NativeFileTypeCoroutineScopeHolder>().coroutineScope

private fun getFallbackCommand(file: VirtualFile): List<String>? {
  val launcher = when (OS.CURRENT) {
    OS.Windows -> listOf("rundll32.exe", "url.dll,FileProtocolHandler")
    OS.macOS -> listOf("/usr/bin/open")
    else -> if (PathEnvironmentVariableUtil.isOnPath("xdg-open")) listOf("xdg-open") else return null
  }
  return launcher + file.presentableUrl
}

private fun startProcess(command: List<String>) {
  try {
    ProcessBuilder(command).start()
  }
  catch (e: Exception) {
    rethrowControlFlowException(e)
    LOG.warn(e)
  }
}

@Service
private class NativeFileTypeCoroutineScopeHolder(@JvmField val coroutineScope: CoroutineScope)
