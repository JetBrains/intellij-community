// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.nioFs.impl

import com.intellij.platform.core.nio.fs.MultiRoutingFsPath
import com.intellij.platform.core.nio.fs.RoutingAwareFileSystemProvider
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.EelDescriptorOwner
import com.intellij.platform.eel.provider.EelNioFsBackend
import java.nio.file.Path

internal class EelNioFsBackendImpl : EelNioFsBackend {
  override fun resolveDescriptor(path: Path): EelDescriptor? {
    val fs = if (path is MultiRoutingFsPath) path.currentDelegate.fileSystem else path.fileSystem
    return (fs as? EelDescriptorOwner)?.eelDescriptor
  }

  override fun isRoutingAware(path: Path): Boolean {
    val provider = path.fileSystem.provider()
    return provider is RoutingAwareFileSystemProvider && provider.canHandleRouting(path)
  }
}
