// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.tcp

import com.intellij.execution.ijent.nio.IjentEphemeralRootAwareFileSystemProvider
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.eel.annotations.MultiRoutingFileSystemPath
import com.intellij.platform.eel.impl.fs.telemetry.TracingFileSystemProvider
import com.intellij.platform.eel.provider.MultiRoutingFileSystemBackend
import com.intellij.platform.ijent.community.impl.IjentFailSafeFileSystemPosixApi
import com.intellij.platform.ijent.community.impl.nio.IjentNioFileSystemProvider
import com.intellij.platform.ijent.tcp.TcpEndpoint
import java.net.URI
import java.nio.file.*
import java.util.concurrent.ConcurrentHashMap


class TcpEelMrfsBackend : MultiRoutingFileSystemBackend {
  companion object {
    private val LOG = logger<TcpEelMrfsBackend>()
  }
  private val cache = ConcurrentHashMap<TcpEndpoint, FileSystem>()
  override fun compute(localFS: FileSystem, sanitizedPath: String): FileSystem? {
    val tcpEndpoint = sanitizedPath.extractTcpEndpoint() ?: return null
    val tcpDescriptor = TcpEelRegistry.getInstance().get(tcpEndpoint) ?: return null
    val localPath = localFS.getPath(tcpDescriptor.rootPathString)
    if (Files.exists(localPath)) {
      LOG.warn("A file system for a path already exists: $localPath")
    }
    return cache.computeIfAbsent(tcpEndpoint) { createFilesystem(it, tcpDescriptor, localPath, localFS) }
  }

  private fun createFilesystem(endpoint: TcpEndpoint, descriptor: TcpEelDescriptor, localPath: Path, localFS: FileSystem): FileSystem {
    val ijentUri = URI("ijent", "tcp", "/${endpoint.toPath()}", null, null)
    val ijentDefaultProvider = TracingFileSystemProvider(IjentNioFileSystemProvider.getInstance())
    val scope = service<TcpEelScopeHolder>().coroutineScope

    try {
      val ijentFs = IjentFailSafeFileSystemPosixApi(scope, descriptor, checkIsIjentInitialized = null)
      ijentDefaultProvider.newFileSystem(ijentUri, IjentNioFileSystemProvider.newFileSystemMap(ijentFs))
    } catch (_: FileSystemAlreadyExistsException) {
      // Nothing.
    }
    return IjentEphemeralRootAwareFileSystemProvider(
      root = localPath,
      ijentFsProvider = ijentDefaultProvider,
      originalFsProvider = TracingFileSystemProvider(localFS.provider()),
      useRootDirectoriesFromOriginalFs = false
    ).getFileSystem(ijentUri)
  }

  override fun getCustomRoots(): Collection<@MultiRoutingFileSystemPath String> {
    return cache.keys.map { "/tcp-${it.toPath()}"}
  }

  override fun getCustomFileStores(localFS: FileSystem): Collection<FileStore> {
    return cache.values.flatMap { it.fileStores }
  }
}