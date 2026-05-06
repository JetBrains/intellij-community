// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.impl.wsl.fileChooser

import com.intellij.openapi.fileChooser.universal.FileWatcherAdapter
import com.intellij.openapi.fileChooser.universal.UniversalFileChooserContributor
import com.intellij.openapi.fileChooser.universal.getFilteredSystemRoots
import com.intellij.platform.eel.impl.fileChooser.EelFileWatcherAdapter
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.ide.impl.wsl.WslEelDescriptor
import java.nio.file.Path

internal class WslFileChooserContributor : UniversalFileChooserContributor {

  override val tabTitle: String = "WSL"

  override suspend fun getRoots(): List<UniversalFileChooserContributor.Root> = getFilteredSystemRoots { path -> ownsPath(path) }

  override fun ownsPath(path: Path): Boolean = path.asEelPath().descriptor is WslEelDescriptor

  override fun getFileWatcherAdapter(): FileWatcherAdapter = EelFileWatcherAdapter()
}
