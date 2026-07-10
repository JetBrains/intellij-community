// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.tcp

import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.eel.EelOsFamily
import com.intellij.platform.eel.annotations.MultiRoutingFileSystemPath
import com.intellij.platform.eel.nioFs.impl.MultiRoutingFileSystemBackend
import com.intellij.platform.eel.provider.utils.WindowsPathUtils
import com.intellij.platform.ijent.community.impl.ijentFailSafeFileSystemApi
import com.intellij.platform.ijent.community.impl.nio.IjentNioFileSystemProvider
import com.intellij.platform.ijent.community.impl.nio.fs.IjentEphemeralRootAwareFileSystemProvider
import kotlinx.coroutines.CoroutineScope
import java.net.URI
import java.nio.file.FileStore
import java.nio.file.FileSystem
import java.nio.file.FileSystemAlreadyExistsException
import java.util.concurrent.ConcurrentHashMap


class TcpEelMrfsBackend(private val scope: CoroutineScope) : MultiRoutingFileSystemBackend {
  companion object {
    private val LOG = logger<TcpEelMrfsBackend>()
  }

  private val cache = ConcurrentHashMap<TcpEelDescriptor, FileSystem>()
  // Per-descriptor seen UNC roots (`<mount>/<server>/<share>`) discovered lazily when paths under
  // them are routed through compute(). VFS needs equality match against Path.of(p).getRoot(), so
  // UNC roots must appear in getCustomRoots(); A..Z synthesis covers only drive letters (IJPL-245397).
  private val seenUncRoots = ConcurrentHashMap<TcpEelDescriptor, MutableSet<String>>()

  override fun compute(localFS: FileSystem, sanitizedPath: String): FileSystem? {
    val (internalName, osFamily) = TcpEelPathParser.extractInternalMachineId(sanitizedPath) ?: return null
    val descriptor = TcpEelPathParser.toDescriptor(internalName, osFamily) ?: return null

    when (descriptor.osFamily) {
      EelOsFamily.Windows -> {
        WindowsPathUtils.extractUncRoot(descriptor.rootPathString, sanitizedPath)?.let { uncRoot ->
          seenUncRoots.computeIfAbsent(descriptor) { ConcurrentHashMap.newKeySet() }.add(uncRoot)
        }
      }
      EelOsFamily.Posix -> Unit
    }

    return cache.computeIfAbsent(descriptor) { createFilesystem(internalName, localFS, descriptor) }
  }

  private fun createFilesystem(internalName: String, localFS: FileSystem, descriptor: TcpEelDescriptor): FileSystem {
    val localPath = localFS.getPath(descriptor.rootPathString)

    val ijentUri = URI("ijent", "tcp", "/$internalName", null, null)
    val ijentDefaultProvider = IjentNioFileSystemProvider.getInstance()

    try {
      val ijentFs = ijentFailSafeFileSystemApi(scope, descriptor, checkIsIjentInitialized = null)
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
      useRootDirectoriesFromOriginalFs = false,
      eelDescriptor = descriptor,
    ).getFileSystem(ijentUri)
  }

  override fun getCustomRoots(): Collection<@MultiRoutingFileSystemPath String> {
    // No I/O - called from read actions; querying ijent.rootDirectories would deploy IJent and block (IJPL-245202).
    // For Windows we synthesize per-drive roots A..Z (VFS equality match with per-drive Path.of(p).getRoot())
    // and append any UNC roots discovered lazily via compute() (see [seenUncRoots]).
    // Non-existent drives just return null from findRoot - VFS does not enumerate them eagerly.
    return cache.keys.flatMap { descriptor ->
      val root = descriptor.rootPathString
      when (descriptor.osFamily) {
        EelOsFamily.Windows -> WindowsPathUtils.expandPerDriveRoots(root) + (seenUncRoots[descriptor] ?: emptySet())
        EelOsFamily.Posix -> listOf(root)
      }
    }
  }

  override fun getCustomFileStores(localFS: FileSystem): Collection<FileStore> {
    return cache.values.flatMap { it.fileStores }
  }
}