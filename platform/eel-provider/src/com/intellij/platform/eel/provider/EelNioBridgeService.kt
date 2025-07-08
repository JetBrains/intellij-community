// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.provider

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.annotations.MultiRoutingFileSystemPath
import com.intellij.platform.eel.isPosix
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.path.EelPathException
import org.jetbrains.annotations.ApiStatus
import java.nio.file.FileSystems
import java.nio.file.Path

/**
 * Converts [EelPath], which is likely a path from a remote machine, to a [Path] for the local machine.
 *
 * Example:
 * ```kotlin
 * EelPath.parse("/home/user", getWslDescriptorSomewhere()).asNioPath() ==
 *     Path.of("""\\wsl.localhost\Ubuntu\home\user""")
 *
 * EelPath.parse("/home/user", getDocekrDescriptorSomewhere()).asNioPath() ==
 *     Path.of("""\\docker\f00b4r\home\user""")
 * ```
 *
 * @throws IllegalArgumentException if the Eel API for [this] does not have a corresponding [java.nio.file.FileSystem]
 */
@Throws(IllegalArgumentException::class)
@ApiStatus.Internal
fun EelPath.asNioPath(): @MultiRoutingFileSystemPath Path =
  asNioPath(null)

/**
 * Converts [EelPath], which is likely a path from a remote machine, to a [Path] for the local machine.
 *
 * The [project] is important for targets like WSL: paths like `\\wsl.localhost\Ubuntu` and `\\wsl$\Ubuntu` are equivalent,
 * but they have different string representation, and some functionality is confused when `wsl.localhost` and `wsl$` are confused.
 * This function helps to choose the proper root according to the base path of the project.
 *
 * Example:
 * ```kotlin
 * val eelPath = EelPath.parse("/home/user", getWslDescriptorSomewhere())
 *
 * eelPath.asNioPath(getProject1()) ==
 *     Path.of("""\\wsl.localhost\Ubuntu\home\user""")
 *
 * eelPath.asNioPath(getProject2()) ==
 *     Path.of("""\\wsl$\Ubuntu\home\user""")
 * ```
 */
@Throws(IllegalArgumentException::class)
@ApiStatus.Internal
fun EelPath.asNioPath(project: Project?): @MultiRoutingFileSystemPath Path {
  return asNioPathOrNull(project)
         ?: throw IllegalArgumentException("Could not convert $this to NIO path, descriptor is $descriptor")
}

private val EelDescriptor.rootsInternal: List<Path>?
  get() = EelProvider.EP_NAME.extensionList
    .firstNotNullOfOrNull { eelProvider -> eelProvider.getCustomRoots(this) }
    ?.takeIf { it.isNotEmpty() }
    ?.map(Path::of)

@get:ApiStatus.Internal
val EelDescriptor.roots: List<Path> get() = rootsInternal ?: error("No roots for $this")

/** See docs for [asNioPath] */
@Deprecated("It never returns null anymore")
@ApiStatus.Internal
fun EelPath.asNioPathOrNull(): @MultiRoutingFileSystemPath Path? =
  asNioPathOrNull(null)

/** See docs for [asNioPath] */
@Deprecated("It never returns null anymore")
@ApiStatus.Internal
fun EelPath.asNioPathOrNull(project: Project?): @MultiRoutingFileSystemPath Path? {
  if (descriptor === LocalEelDescriptor) {
    return Path.of(toString())
  }
  val eelRoots = descriptor.rootsInternal

  // Comparing strings because `Path.of("\\wsl.localhost\distro\").equals(Path.of("\\wsl$\distro\")) == true`
  // If the project works with `wsl$` paths, this function must return `wsl$` paths, and the same for `wsl.localhost`.
  val projectBasePathNio = project?.basePath?.let(Path::of)

  LOG.trace {
    "asNioPathOrNull():" +
    " path=$this" +
    " project=$project" +
    " descriptor=$descriptor" +
    " eelRoots=${eelRoots?.joinToString(prefix = "[", postfix = "]", separator = ", ") { path -> "$path (${path.javaClass.name})" }}" +
    " projectBasePathNio=$projectBasePathNio"
  }

  if (eelRoots == null) {
    return null
  }

  val eelRoot: Path = asNioPathOrNullImpl(projectBasePathNio, eelRoots, this)

  @MultiRoutingFileSystemPath
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
 * Converts a path generated by the default NIO filesystem to [EelPath].
 *
 * Example:
 * ```kotlin
 * Path.of("""C:\Windows""").asEelPath() ==
 *     EelPath.parse("""C:\Windows""", LocalEelDescriptor)
 *
 * Path.of("""\\wsl$\Ubuntu\usr""").asEelPath() ==
 *     EelPath.parse("/usr", someWslDescriptor)
 * ```
 *
 * @throws IllegalArgumentException if the passed path cannot be mapped to a path corresponding to Eel.
 * It can happen if [this] belongs to a [java.nio.file.FileSystem] that was not registered as a backend of `MultiRoutingFileSystemProvider`
 *
 * @throws EelPathException if the passed path is not an absolute path.
 */
@Throws(IllegalArgumentException::class, EelPathException::class)
@ApiStatus.Internal
fun Path.asEelPath(): EelPath {
  if (fileSystem != FileSystems.getDefault()) {
    throw IllegalArgumentException("Could not convert $this to EelPath: the path does not belong to the default NIO FileSystem")
  }
  val (descriptor, eelProvider) =
    EelProvider.EP_NAME.extensionList
      .firstNotNullOfOrNull { eelProvider ->
        eelProvider.getEelDescriptor(this)?.to(eelProvider)
      }
    ?: return EelPath.parse(toString(), LocalEelDescriptor)

  val root =
    eelProvider.getCustomRoots(descriptor)
      ?.map { rootStr -> Path.of(rootStr) }
      ?.firstOrNull { rootCandidate -> startsWith(rootCandidate) }
    ?: throw NoSuchElementException("No roots for $descriptor match $this: ${eelProvider.getCustomRoots(descriptor)}")

  val relative = root.relativize(this)
  if (descriptor.osFamily.isPosix) {
    return relative.fold(EelPath.parse("/", descriptor), { path, part -> path.resolve(part.toString()) })
  }
  else {
    TODO() // on Windows, we need additional logic to guess the new root
  }
}

@ApiStatus.Internal
fun EelDescriptor.routingPrefixes(): Set<Path> {
  return EelProvider.EP_NAME.extensionList
    .flatMapTo(HashSet()) { eelProvider ->
      eelProvider.getCustomRoots(this)?.map(Path::of) ?: emptySet()
    }
}

private class EelNioBridgeService

private val LOG = logger<EelNioBridgeService>()