// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.ijent.nio

import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.core.nio.fs.DelegatingFileSystem
import com.intellij.platform.core.nio.fs.DelegatingFileSystemProvider
import com.intellij.platform.eel.provider.EelNioBridgeService
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.ijent.IjentApi
import com.intellij.platform.ijent.community.impl.nio.IjentNioFileSystemProvider
import com.intellij.platform.ijent.community.impl.nio.telemetry.TracingFileSystemProvider
import com.intellij.util.awaitCancellationAndInvoke
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import java.net.URI
import java.nio.file.FileSystem
import java.nio.file.FileSystemAlreadyExistsException
import java.nio.file.Path
import java.nio.file.spi.FileSystemProvider
import kotlin.io.path.Path
import kotlin.io.path.exists

/**
 * Allows registering custom file systems
 */
@ApiStatus.Internal
fun CoroutineScope.registerIjentNioFs(
  ijent: IjentApi,
  root: String,
  internalName: String,
  authority: String,
  recomputeIfRegistered: Boolean = true,
  wrapFileSystemProvider: ((FileSystemProvider) -> DelegatingFileSystemProvider<*, *>)? = null,
): Path {
  val service = EelNioBridgeService.getInstanceSync()

  if (!recomputeIfRegistered) {
    val rootPath = Path(root)
    val descriptor = service.tryGetEelDescriptor(rootPath)

    if (descriptor != null && descriptor !== LocalEelDescriptor) {
      check(rootPath.exists())
      return rootPath
    }
  }

  val uri = URI("ijent", authority, FileUtil.toSystemIndependentName(root), null, null)

  try {
    IjentNioFileSystemProvider.getInstance().newFileSystem(uri, IjentNioFileSystemProvider.newFileSystemMap(ijent.fs))
  }
  catch (_: FileSystemAlreadyExistsException) {
    // Nothing.
  }

  service.register(root, ijent.descriptor, internalName, true, false) { underlyingProvider, previousFs ->
    // Compute a path before custom fs registration. Usually should represent a non-existent local path
    val localPath = Path(root).also { check(!it.exists()) }

    IjentEphemeralRootAwareFileSystemProvider(
      root = localPath,
      delegate = TracingFileSystemProvider(IjentNioFileSystemProvider.getInstance())
    ).let { wrapFileSystemProvider?.invoke(it) ?: it }.getFileSystem(uri)
  }

  this.awaitCancellationAndInvoke {
    service.unregister(ijent.descriptor)
  }

  // Compute a path after registration
  return Path(root)
}