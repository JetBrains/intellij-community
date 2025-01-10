// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.ijent.nio

import com.intellij.openapi.components.service
import com.intellij.platform.eel.provider.EelNioBridgeService
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.ijent.IjentApi
import com.intellij.platform.ijent.community.impl.nio.IjentNioFileSystemProvider
import com.intellij.platform.ijent.community.impl.nio.telemetry.TracingFileSystemProvider
import com.intellij.util.application
import com.intellij.util.awaitCancellationAndInvoke
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.net.URI
import java.nio.file.FileSystemAlreadyExistsException
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists

/**
 * Allows registering custom file systems
 */
@ApiStatus.Internal
fun CoroutineScope.registerIjentNioFs(ijent: IjentApi, root: String, authority: String, recomputeIfRegistered: Boolean = true): Path {
  val service = application.service<EelNioBridgeService>()

  if (!recomputeIfRegistered) {
    val rootPath = Path(root)
    val descriptor = service.tryGetEelDescriptor(rootPath)

    if (descriptor != null && descriptor !== LocalEelDescriptor) {
      check(rootPath.exists())
      return rootPath
    }
  }

  val uri = URI("ijent", authority, root, null, null)

  try {
    IjentNioFileSystemProvider.getInstance().newFileSystem(uri, IjentNioFileSystemProvider.newFileSystemMap(ijent.fs))
  }
  catch (_: FileSystemAlreadyExistsException) {
    // Nothing.
  }

  service.register(root, ijent.descriptor, true, false) { underlyingProvider, previousFs ->
    // Compute a path before custom fs registration. Usually should represent a non-existent local path
    val localPath = Path(root).also { check(!it.exists()) }

    IjentEphemeralRootAwareFileSystemProvider(
      root = localPath,
      delegate = TracingFileSystemProvider(IjentNioFileSystemProvider.getInstance())
    ).getFileSystem(uri)
  }

  this.launch {
    awaitCancellationAndInvoke {
      service.deregister(ijent.descriptor)
    }
  }

  // Compute a path after registration
  return Path(root)
}