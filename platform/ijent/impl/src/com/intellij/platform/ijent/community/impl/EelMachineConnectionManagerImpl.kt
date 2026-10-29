// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.community.impl

import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelMachineConnectionProvider
import com.intellij.platform.eel.EelMachineNioFileSystemProvider
import com.intellij.platform.eel.nioFs.impl.nioFsUri
import com.intellij.platform.eel.provider.localEel
import com.intellij.platform.ijent.IjentApi
import com.intellij.platform.ijent.community.impl.nio.IjentNioFileSystemProvider
import java.nio.file.FileSystem

internal class EelMachineConnectionManagerImpl : EelMachineConnectionProvider, EelMachineNioFileSystemProvider {
  override fun createFileSystemForEelApi(eelApi: EelApi): FileSystem? {
    if (eelApi !is IjentApi) return null
    val uri = eelApi.machine.nioFsUri ?: return null
    val provider = IjentNioFileSystemProvider.getInstance()
    // Remove any existing registration for this URI so that the FileSystem uses the current session.
    provider.close(uri)
    return provider.newFileSystem(
      uri,
      IjentNioFileSystemProvider.newFileSystemMap(eelApi.fs),
    )
  }

  override fun isSessionStillAlive(eelApi: EelApi): Boolean {
    return when (eelApi) {
      localEel -> true
      is IjentApi -> eelApi.isRunning
      else -> {
        logger<EelMachineConnectionManagerImpl>().error("Unknown EelApi type $eelApi")
        true
      }
    }
  }
}
