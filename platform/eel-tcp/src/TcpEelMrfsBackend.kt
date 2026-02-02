// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.tcp

import com.intellij.execution.ijent.nio.IjentEphemeralRootAwareFileSystemProvider
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.eel.annotations.MultiRoutingFileSystemPath
import com.intellij.platform.eel.provider.MultiRoutingFileSystemBackend
import com.intellij.platform.ijent.community.impl.IjentFailSafeFileSystemPosixApi
import com.intellij.platform.ijent.community.impl.nio.IjentNioFileSystemProvider
import kotlinx.coroutines.CoroutineScope
import java.net.URI
import java.nio.file.FileStore
import java.nio.file.FileSystem
import java.nio.file.FileSystemAlreadyExistsException
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap


class TcpEelMrfsBackend(private val scope: CoroutineScope) : MultiRoutingFileSystemBackend {
  companion object {
    private val LOG = logger<TcpEelMrfsBackend>()
  }

  private val cache = ConcurrentHashMap<String, FileSystem>()

  override fun compute(localFS: FileSystem, sanitizedPath: String): FileSystem? {
    val internalName = TcpEelPathParser.extractInternalMachineId(sanitizedPath) ?: return null
    val descriptor = TcpEelPathParser.toDescriptor(internalName) ?: return null

    return cache.computeIfAbsent(internalName) { createFilesystem(internalName, localFS, descriptor) }
  }

  private fun createFilesystem(internalName: String, localFS: FileSystem, descriptor: TcpEelDescriptor): FileSystem {
    val localPath = localFS.getPath(descriptor.rootPathString)
    if (Files.exists(localPath)) {
      LOG.warn("Cannot create TCP filesystem: local path already exists: $localPath")
    }

    val ijentUri = URI("ijent", "tcp", "/$internalName", null, null)
    val ijentDefaultProvider = IjentNioFileSystemProvider.getInstance()

    try {
      val ijentFs = IjentFailSafeFileSystemPosixApi(scope, descriptor, checkIsIjentInitialized = null)
      ijentDefaultProvider.newFileSystem(ijentUri, IjentNioFileSystemProvider.newFileSystemMap(ijentFs))
    }
    catch (_: FileSystemAlreadyExistsException) {
      // Nothing.
    }
    LOG.info("New FileSystem initialized for $internalName at $localPath and URI=$ijentUri")
    return IjentEphemeralRootAwareFileSystemProvider(
      root = localPath,
      ijentFsProvider = ijentDefaultProvider,
      originalFsProvider = localFS.provider(),
      useRootDirectoriesFromOriginalFs = false
    ).getFileSystem(ijentUri)
  }

  override fun getCustomRoots(): Collection<@MultiRoutingFileSystemPath String> {
    return cache.keys.map { "${TcpEelConstants.TCP_PROTOCOL_PREFIX}$it" }
  }

  override fun getCustomFileStores(localFS: FileSystem): Collection<FileStore> {
    return cache.values.flatMap { it.fileStores }
  }
}