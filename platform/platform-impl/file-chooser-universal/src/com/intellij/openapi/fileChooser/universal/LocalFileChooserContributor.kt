// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser.universal

import com.intellij.openapi.fileChooser.actions.FileChooserActionUtil
import com.intellij.openapi.fileChooser.universal.UniversalFileChooserContributor.Companion.getFilteredSystemRoots
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.ohos.OhosSandboxRoots
import com.intellij.util.system.LowLevelLocalMachineAccess
import com.intellij.util.system.OS
import java.nio.file.Path

internal class LocalFileChooserContributor : UniversalFileChooserContributor {
  override val tabTitle: String = "Local"

  @OptIn(LowLevelLocalMachineAccess::class)
  override suspend fun getRoots(): List<UniversalFileChooserContributor.Root> =
    if (OS.CURRENT == OS.OHOS && OhosSandboxRoots.isEnabled)
      getOhosSandboxRoots()
    else
      getFilteredSystemRoots { path -> ownsPath(path) }

  override fun ownsPath(path: Path): Boolean = path.asEelPath().descriptor is LocalEelDescriptor

  override fun getFileWatcherAdapter(): FileWatcherAdapter = LocalFileWatcherAdapter()

  override fun getDesktopPath(): Path? {
    return FileChooserActionUtil.getDesktopDir()
  }

  private fun getOhosSandboxRoots(): List<UniversalFileChooserContributor.Root> =
    OhosSandboxRoots.paths.filter(::ownsPath).map { path ->
      val name = path.toString()
      UniversalFileChooserContributor.Root(name, UniversalFileChooserContributor.Presentation(name), path)
    }
}
