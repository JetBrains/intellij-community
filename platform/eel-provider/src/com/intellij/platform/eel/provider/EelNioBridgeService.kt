// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.provider

import com.intellij.openapi.application.ApplicationManager
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.path.EelPath
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.spi.FileSystemProvider
import kotlin.jvm.Throws

/**
 * A service that is responsible for mapping between instances of [EelPath] and [Path]
 */
interface EelNioBridgeService {

  /**
   * @return `null` if [nioPath] belongs to a [java.nio.file.FileSystem] that was not registered as a backend of `MultiRoutingFileSystemProvider`
   */
  fun tryGetEelDescriptor(nioPath: Path): EelDescriptor?

  /**
   * @return `null`  if the Eel API for [eelDescriptor] does not have a corresponding [java.nio.file.FileSystem]
   */
  fun tryGetNioRoot(eelDescriptor: EelDescriptor): Path?

  /**
   * Registers custom eel as a nio file system
   */
  fun register(localRoot: String, descriptor: EelDescriptor, prefix: Boolean, caseSensitive: Boolean, fsProvider: (underlyingProvider: FileSystemProvider, previousFs: FileSystem?) -> FileSystem?)

  /**
   * Deregisters custom eel as a nio file system
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
  val service = ApplicationManager.getApplication().getService(EelNioBridgeService::class.java)
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
    return throw IllegalArgumentException("Could not convert $this to EelPath: the path does not belong to the default NIO FileSystem")
  }
  val service = ApplicationManager.getApplication().getService(EelNioBridgeService::class.java)
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
  return ApplicationManager.getApplication().getService(EelNioBridgeService::class.java).tryGetNioRoot(this)
         ?: throw IllegalArgumentException("Could not convert $this to EelPath: the path does not belong to the default NIO FileSystem")
}