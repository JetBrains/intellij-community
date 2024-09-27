// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.ijent.nio

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.core.nio.fs.MultiRoutingFileSystemProvider
import com.intellij.platform.ijent.IjentApi
import com.intellij.platform.ijent.community.impl.nio.IjentNioFileSystemProvider
import com.intellij.platform.ijent.community.impl.nio.telemetry.TracingFileSystemProvider
import org.jetbrains.annotations.ApiStatus
import java.net.URI
import java.nio.file.FileSystem
import java.nio.file.FileSystemAlreadyExistsException
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.spi.FileSystemProvider
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.Path
import kotlin.io.path.exists

/**
 * Service for registering custom file systems, typically remote ones.
 *
 *  Usage:
 *
 * ```kotlin
 * val ijentRegistry = IjentNioFsRegistry.instance()
 * val ijentPath = ijentRegistry.registerFs(ijentApi, <root>, <authority (wsl/docker/etc.)>)
 * ```
 */
// TODO: merge it with IjentWslNioFsToggler/IjentNioFsStrategy
@ApiStatus.Internal
@Service
class IjentNioFsRegistry private constructor() {
  companion object {
    suspend fun instanceAsync(): IjentNioFsRegistry = serviceAsync()
    fun instance(): IjentNioFsRegistry = service()
  }

  fun isAvailable() = registry != null

  fun registerFs(ijent: IjentApi, root: String, authority: String): Path {
    registry ?: error("Not available")

    val uri = URI("ijent", authority, root, null, null)

    try {
      IjentNioFileSystemProvider.getInstance().newFileSystem(uri, IjentNioFileSystemProvider.newFileSystemMap(ijent.fs))
    }
    catch (_: FileSystemAlreadyExistsException) {
      // Nothing.
    }

    registry.computeIfAbsent(root) {
      // Compute a path before custom fs registration. Usually should represent a non-existent local path
      val localPath = Path(root).also { check(!it.exists()) }

      IjentEphemeralRootAwareFileSystemProvider(
        root = localPath,
        delegate = TracingFileSystemProvider(IjentNioFileSystemProvider.getInstance())
      ).getFileSystem(uri)
    }

    // TODO: IjentApi should contains something like onTerminated(block: () -> Unit)
    // ijent.onTerminated {
    //    registry.remove(root).close()
    //}

    // Compute a path after registration
    return Path(root)
  }

  private val registry = run {
    val defaultProvider = FileSystems.getDefault().provider()

    if (defaultProvider.javaClass.name == MultiRoutingFileSystemProvider::class.java.name) {
      FileSystemsRegistry(defaultProvider)
    }
    else {
      logger<IjentNioFsRegistry>().warn(
        "The default filesystem ${FileSystems.getDefault()} is not ${MultiRoutingFileSystemProvider::class.java}"
      )
      null
    }
  }
}

private class FileSystemsRegistry(private val multiRoutingFileSystemProvider: FileSystemProvider) {
  private val own: MutableMap<String, FileSystem> = ConcurrentHashMap()

  fun computeIfAbsent(root: String, compute: (String) -> FileSystem) {
    MultiRoutingFileSystemProvider.computeBackend(multiRoutingFileSystemProvider, root, true, true) { _, _ ->
      own.computeIfAbsent(root, compute)
    }
  }
}