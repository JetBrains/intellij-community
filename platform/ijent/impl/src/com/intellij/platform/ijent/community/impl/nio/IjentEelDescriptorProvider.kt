// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.community.impl.nio

import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelMachine
import com.intellij.platform.eel.annotations.MultiRoutingFileSystemPath
import com.intellij.platform.eel.provider.EelProvider
import java.nio.file.Path

internal class IjentEelDescriptorProvider : EelProvider {
  override suspend fun tryInitialize(path: @MultiRoutingFileSystemPath String): EelMachine? {
    return null
  }

  override fun getEelDescriptor(path: Path): EelDescriptor? {
    if (path is IjentNioPath) {
      return path.nioFs.ijentFs.descriptor
    }
    else {
      return null
    }
  }

  override fun getCustomRoots(eelDescriptor: EelDescriptor): Collection<@MultiRoutingFileSystemPath String>? {
    return null
  }
}