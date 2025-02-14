// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.provider

import com.intellij.openapi.util.Ref
import com.intellij.platform.core.nio.fs.MultiRoutingFileSystemProvider
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.EelNioBridgeService
import com.intellij.util.containers.forEachGuaranteed
import org.jetbrains.annotations.ApiStatus
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.spi.FileSystemProvider
import java.util.concurrent.ConcurrentHashMap

@ApiStatus.Internal
class EelNioBridgeServiceImpl : EelNioBridgeService {
  private val multiRoutingFileSystemProvider = FileSystems.getDefault().provider()

  private val rootRegistry = ConcurrentHashMap<EelDescriptor, MutableSet<Path>>()
  private val fsRegistry = ConcurrentHashMap<String, FileSystem>()
  private val idRegistry = ConcurrentHashMap<EelDescriptor, String>()

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

    rootRegistry.computeIfAbsent(descriptor) { mutableSetOf() }.add(Path.of(localRoot))
    idRegistry[descriptor] = internalName
  }

  override fun deregister(descriptor: EelDescriptor) {
    val roots = rootRegistry.remove(descriptor)
    require(roots != null) { "Attempt to deregister unknown $descriptor" }
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
  }
}