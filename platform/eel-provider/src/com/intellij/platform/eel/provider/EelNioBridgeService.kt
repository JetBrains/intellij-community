// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.provider

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.path.EelPath
import org.jetbrains.annotations.NonNls
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.spi.FileSystemProvider

/**
 * A service that is responsible for mapping between instances of [EelPath] and [Path]
 */
interface EelNioBridgeService {

  companion object {
    @JvmStatic
    fun getInstanceSync(): EelNioBridgeService = ApplicationManager.getApplication().service()

    @JvmStatic
    suspend fun getInstance(): EelNioBridgeService = ApplicationManager.getApplication().serviceAsync()
  }

  /**
   * @return `null` if [nioPath] belongs to a [java.nio.file.FileSystem] that was not registered as a backend of `MultiRoutingFileSystemProvider`
   */
  fun tryGetEelDescriptor(nioPath: Path): EelDescriptor?

  /**
   * @return `null`  if the Eel API for [eelDescriptor] does not have a corresponding [java.nio.file.FileSystem]
   */
  fun tryGetNioRoot(eelDescriptor: EelDescriptor): Path?

  /**
   * @return The `internalName` from [register] that was provided alongside [eelDescriptor].
   */
  fun tryGetId(eelDescriptor: EelDescriptor): String?

  /**
   * @return the descriptor that was provided alongside `internalName` during [register].
   */
  fun tryGetDescriptorByName(name: String): EelDescriptor?

  /**
   * Registers custom eel as a nio file system
   * @param internalName An ascii name of the descriptor that can be used as internal ID of the registered environment.
   */
  fun register(localRoot: String, descriptor: EelDescriptor, internalName: @NonNls String, prefix: Boolean, caseSensitive: Boolean, fsProvider: (underlyingProvider: FileSystemProvider, previousFs: FileSystem?) -> FileSystem?)

  /**
   * Removes the registered NIO File System associated with [descriptor]
   */
  fun deregister(descriptor: EelDescriptor)
}

/**
 * Inverse of [asEelPath].
 *
 * @throws IllegalArgumentException if the Eel API for [this] does not have a corresponding [java.nio.file.FileSystem]
 */
@Throws(IllegalArgumentException::class)
fun EelPath.asNioPath(): Path {
  return asNioPathOrNull()
         ?: throw IllegalArgumentException("Could not convert $this to nio.Path: the corresponding provider for $descriptor is not registered in ${EelNioBridgeService::class.simpleName}")
}

fun EelPath.asNioPathOrNull(): Path? {
  if (descriptor === LocalEelDescriptor) {
    return Path.of(toString())
  }
  val service = EelNioBridgeService.getInstanceSync()
  val root = service.tryGetNioRoot(descriptor) ?: return null
  return parts.fold(root, Path::resolve)
}

/**
 * Inverse of [asNioPath].
 *
 * @throws IllegalArgumentException if the passed path cannot be mapped to a path corresponding to Eel.
 * It can happen if [this] belongs to a [java.nio.file.FileSystem] that was not registered as a backend of `MultiRoutingFileSystemProvider`
 */
@Throws(IllegalArgumentException::class)
fun Path.asEelPath(): EelPath {
  if (fileSystem != FileSystems.getDefault()) {
    throw IllegalArgumentException("Could not convert $this to EelPath: the path does not belong to the default NIO FileSystem")
  }
  val service = EelNioBridgeService.getInstanceSync()
  val descriptor = service.tryGetEelDescriptor(this) ?: return EelPath.parse(toString(), LocalEelDescriptor)
  val root = service.tryGetNioRoot(descriptor) ?: error("unreachable") // since the descriptor is not null, the root should be as well
  val relative = root.relativize(this)
  if (descriptor.operatingSystem == EelPath.OS.UNIX) {
    return relative.fold(EelPath.parse("/", descriptor), { path, part -> path.resolve(part.toString()) })
  }
  else {
    TODO() // on Windows, we need additional logic to guess the new root
  }
}

fun EelDescriptor.routingPrefix(): Path {
  return EelNioBridgeService.getInstanceSync().tryGetNioRoot(this)
         ?: throw IllegalArgumentException("Failure of obtaining prefix: could not convert $this to EelPath. The path does not belong to the default NIO FileSystem")
}