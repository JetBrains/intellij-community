// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.impl.wsl.fileChooser

import com.intellij.core.CoreBundle
import com.intellij.openapi.fileChooser.universal.FileWatcherAdapter
import com.intellij.openapi.fileChooser.universal.UniversalFileChooserContributor
import com.intellij.openapi.fileChooser.universal.UniversalFileChooserContributor.Companion.getFilteredSystemRoots
import com.intellij.platform.eel.impl.fileChooser.EelFileWatcherAdapter
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.ide.impl.wsl.WslEelDescriptor
import org.jetbrains.annotations.Nls
import java.nio.file.Path

internal class WslFileChooserContributor : UniversalFileChooserContributor {

  override val tabTitle: String = "WSL"

  override suspend fun getRoots(): List<UniversalFileChooserContributor.Root> = getFilteredSystemRoots { path -> ownsPath(path) }

  override suspend fun getFilteredRoots(path: Path): List<UniversalFileChooserContributor.Root> {
    if (path.getEelDescriptor() is WslEelDescriptor) {
      return listOf(UniversalFileChooserContributor.asDefaultRoot(path.root))
    }
    return emptyList()
  }

  override fun ownsPath(path: Path): Boolean = path.asEelPath().descriptor is WslEelDescriptor

  override fun getFileWatcherAdapter(): FileWatcherAdapter = EelFileWatcherAdapter()

  override fun getCustomLoadingText(): @Nls String? {
    return CoreBundle.message("file.chooser.loading.the.installed.wsl.distributions.list")
  }
}
