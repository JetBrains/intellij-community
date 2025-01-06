// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.provider

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Ref
import com.intellij.platform.core.nio.fs.MultiRoutingFileSystemProvider
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.EelNioBridgeService
import org.jetbrains.annotations.ApiStatus
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.spi.FileSystemProvider
import java.util.concurrent.ConcurrentHashMap

@ApiStatus.Internal
class EelNioBridgeServiceImpl : EelNioBridgeService {
  private val multiRoutingFileSystemProvider = FileSystems.getDefault().provider()

  private val rootRegistry = ConcurrentHashMap<EelDescriptor, Path>()
  private val fsRegistry = ConcurrentHashMap<String, FileSystem>()

  companion object {
    private val LOG = Logger.getInstance(EelNioBridgeServiceImpl::class.java)
  }

  override fun tryGetEelDescriptor(nioPath: Path): EelDescriptor? {
    val descriptor = rootRegistry.entries.find { nioPath.startsWith(it.value) }?.key ?: return null
    return descriptor
  }

  override fun tryGetNioRoot(eelDescriptor: EelDescriptor): Path? {
    return rootRegistry[eelDescriptor]
  }

  override fun register(localRoot: String, descriptor: EelDescriptor, prefix: Boolean, caseSensitive: Boolean, fsProvider: (underlyingProvider: FileSystemProvider, existingFileSystem: FileSystem?) -> FileSystem?) {
    fsRegistry.compute(localRoot) { _, existingFileSystem ->
      val result: Ref<FileSystem?> = Ref(null)
      // the computation within MultiRoutingFileSystem can be restarted several times, but it will not terminate until it succeeds
      MultiRoutingFileSystemProvider.computeBackend(multiRoutingFileSystemProvider, localRoot, prefix, caseSensitive) { underlyingProvider, actualFs ->
        require(existingFileSystem == actualFs)
        val newFileSystem = fsProvider(underlyingProvider, existingFileSystem)
        result.set(newFileSystem)
        newFileSystem
      }
      result.get()
    }
    val currentValue = rootRegistry[descriptor]
    val newRoot = Path.of(localRoot)
    rootRegistry[descriptor] = Path.of(localRoot)
    if (currentValue != null && currentValue != newRoot) {
      LOG.warn("Replacing EEL root for $descriptor from $currentValue to $newRoot")
    }
  }

  override fun deregister(descriptor: EelDescriptor) {
    val existingValue = rootRegistry.remove(descriptor)
    require(existingValue != null) { "Attempt to deregister unknown root for $descriptor" }
    val localRoot = existingValue.toString()
    fsRegistry.compute(existingValue.toString()) { _, existingFileSystem ->
      MultiRoutingFileSystemProvider.computeBackend(multiRoutingFileSystemProvider, localRoot, false, false) { underlyingProvider, actualFs ->
        require(existingFileSystem == actualFs)
        try {
          existingFileSystem?.close()
        }
        catch (_: UnsupportedOperationException) {
          // ignored, FS doesn't want to be closed
        }
        null
      }
      null
    }
  }
}