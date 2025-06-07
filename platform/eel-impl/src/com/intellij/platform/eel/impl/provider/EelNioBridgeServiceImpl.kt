// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.provider

import com.intellij.openapi.util.Ref
import com.intellij.platform.core.nio.fs.MultiRoutingFileSystemProvider
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.EelNioBridgeService
import com.intellij.util.containers.forEachGuaranteed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.io.Closeable
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.spi.FileSystemProvider
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.Path
import kotlin.io.path.pathString

@ApiStatus.Internal
@VisibleForTesting
class EelNioBridgeServiceImpl(coroutineScope: CoroutineScope) : EelNioBridgeService {
  private val multiRoutingFileSystemProvider = FileSystems.getDefault().provider()

  private val rootRegistry = ConcurrentHashMap<EelDescriptor, MutableSet<Path>>()
  private val fsRegistry = ConcurrentHashMap<String, FileSystem>()
  private val idRegistry = ConcurrentHashMap<EelDescriptor, String>()

  init {
    coroutineScope.coroutineContext.job.invokeOnCompletion {
      idRegistry.keys().asSequence().forEach { unregister(it) }
    }
  }

  override fun tryGetEelDescriptor(nioPath: Path): EelDescriptor? {
    return rootRegistry.entries.asSequence()
      .flatMap { (descriptor, paths) -> paths.map { path -> descriptor to path } }
      .find { (_, path) -> nioPath.startsWith(path) }
      ?.first
  }

  override fun tryGetNioRoots(eelDescriptor: EelDescriptor): Set<Path>? {
    return rootRegistry[eelDescriptor]?.toSet()
  }

  override fun tryGetId(eelDescriptor: EelDescriptor): String? {
    return idRegistry[eelDescriptor]
  }

  override fun tryGetDescriptorByName(name: String): EelDescriptor? {
    return idRegistry.entries.find { it.value == name }?.key
  }

  override fun register(localRoot: String, descriptor: EelDescriptor, internalName: String, prefix: Boolean, caseSensitive: Boolean, fsProvider: (underlyingProvider: FileSystemProvider, existingFileSystem: FileSystem?) -> FileSystem?) {
    // For some reason, Path.of on Windows adds an extra slash at the end. For example, \\docker\id becomes \\docker\id\. Therefore, to compute the key, we need to convert the path to this format.
    val key = Path(localRoot).pathString

    fsRegistry.compute(key) { _, existingFileSystem ->
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

    rootRegistry.computeIfAbsent(descriptor) { mutableSetOf() }.add(Path.of(localRoot))
    idRegistry[descriptor] = internalName
  }

  override fun unregister(descriptor: EelDescriptor): Boolean {
    val roots = rootRegistry.remove(descriptor) ?: return false

    roots.forEachGuaranteed { localRoot ->
      fsRegistry.compute(localRoot.toString()) { _, existingFileSystem ->
        MultiRoutingFileSystemProvider.computeBackend(multiRoutingFileSystemProvider, localRoot.toString(), false, false) { underlyingProvider, actualFs ->
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

    return true
  }
}