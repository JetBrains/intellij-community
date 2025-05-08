// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.provider

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelPlatform
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
  fun tryGetNioRoots(eelDescriptor: EelDescriptor): Set<Path>?

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
   * Returns `true` if [descriptor] was successfully unregistered.
   * Returns `false` if [descriptor] had already been removed earlier
   * or have never been registered.
   */
  fun unregister(descriptor: EelDescriptor): Boolean
}

/**
 * Inverse of [asEelPath].
 *
 * @throws IllegalArgumentException if the Eel API for [this] does not have a corresponding [java.nio.file.FileSystem]
 */
@Throws(IllegalArgumentException::class)
fun EelPath.asNioPath(): Path =
  asNioPath(null)

/**
 * The [project] is important for targets like WSL: paths like `\\wsl.localhost\Ubuntu` and `\\wsl$\Ubuntu` are equivalent,
 * but they have different string representation, and some functionality is confused when `wsl.localhost` and `wsl$` are confused.
 * This function helps to choose the proper root according to the base path of the project.
 */
@Throws(IllegalArgumentException::class)
fun EelPath.asNioPath(project: Project?): Path {
  return asNioPathOrNull(project)
         ?: throw IllegalArgumentException("Could not convert $this to nio.Path: the corresponding provider for $descriptor is not registered in ${EelNioBridgeService::class.simpleName}")
}

/**
 * The [project] is important for targets like WSL: paths like `\\wsl.localhost\Ubuntu` and `\\wsl$\Ubuntu` are equivalent,
 * but they have different string representation, and some functionality is confused when `wsl.localhost` and `wsl$` are confused.
 * This function helps to choose the proper root according to the base path of the project.
 */
fun EelPath.asNioPathOrNull(): Path? =
  asNioPathOrNull(null)

fun EelPath.asNioPathOrNull(project: Project?): Path? {
  if (descriptor === LocalEelDescriptor) {
    return Path.of(toString())
  }
  val service = EelNioBridgeService.getInstanceSync()
  val eelRoots = service.tryGetNioRoots(descriptor)?.takeIf { it.isNotEmpty() }

  // Comparing strings because `Path.of("\\wsl.localhost\distro\").equals(Path.of("\\wsl$\distro\")) == true`
  // If the project works with `wsl$` paths, this function must return `wsl$` paths, and the same for `wsl.localhost`.
  val projectBasePathNio = project?.basePath?.let(Path::of)

  LOG.trace {
    "asNioPathOrNull():" +
    " path=$this" +
    " project=$project" +
    " descriptor=$descriptor" +
    " eelRoots=${eelRoots?.joinToString(prefix = "[", postfix = "]", separator = ", ") { path -> "$path (${path.javaClass.name})"}}" +
    " projectBasePathNio=$projectBasePathNio"
  }

  if (eelRoots == null) {
    return null
  }

  val eelRoot: Path = asNioPathOrNullImpl(projectBasePathNio, eelRoots, this)

  val result = parts.fold(eelRoot, Path::resolve)
  LOG.trace {
    "asNioPathOrNull(): path=$this project=$project result=$result"
  }
  return result
}

/**
 * Choosing between not only paths belonging to the project, but also paths with the same root, e.g., mount drive on Windows.
 * It's possible that some code in the project tries to access the file outside the project, f.i., accessing `~/.m2`.
 *
 * This function also tries to preserve the case in case-insensitive file systems, because some other parts of the IDE
 * may compare paths as plain string despite the incorrectness of that approach.
 */
private fun asNioPathOrNullImpl(basePath: Path?, eelRoots: Collection<Path>, sourcePath: EelPath): Path {
  if (basePath != null) {
    for (eelRoot in eelRoots) {
      if (basePath.startsWith(eelRoot)) {
        var resultPath = basePath.root
        if (eelRoot.nameCount > 0) {
          resultPath = resultPath.resolve(basePath.subpath(0, eelRoot.nameCount))
        }
        return resultPath
      }
    }
  }

  return eelRoots.first()
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
  val root = service.tryGetNioRoots(descriptor)?.firstOrNull { this.startsWith(it) } ?: error("unreachable") // since the descriptor is not null, the root should be as well
  val relative = root.relativize(this)
  if (descriptor.platform is EelPlatform.Posix) {
    return relative.fold(EelPath.parse("/", descriptor), { path, part -> path.resolve(part.toString()) })
  }
  else {
    TODO() // on Windows, we need additional logic to guess the new root
  }
}

fun EelDescriptor.routingPrefixes(): Set<Path> {
  return EelNioBridgeService.getInstanceSync().tryGetNioRoots(this)
         ?: throw IllegalArgumentException("Failure of obtaining prefix: could not convert $this to EelPath. The path does not belong to the default NIO FileSystem")
}

private val LOG = logger<EelNioBridgeService>()