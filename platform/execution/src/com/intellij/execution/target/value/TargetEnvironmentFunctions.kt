// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("TargetEnvironmentFunctions")

package com.intellij.execution.target.value

import com.intellij.execution.target.*
import com.intellij.execution.target.local.LocalTargetEnvironment
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.annotations.ApiStatus
import java.io.File
import java.io.IOException
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.Paths
import java.util.function.Function
import kotlin.io.path.name

/**
 * The function that is expected to be resolved with provided
 * [TargetEnvironment].
 *
 * Such functions could be used during the construction of a command line and
 * play the role of deferred values of, for example:
 *  - the path to an executable;
 *  - the working directory;
 *  - the command-line parameters;
 *  - the values of environment variables.
 *
 *  It is recommended to subclass the default implementation [TraceableTargetEnvironmentFunction], see its documentation for rationale.
 */
typealias TargetEnvironmentFunction<R> = Function<TargetEnvironment, R>

/**
 * Implementation of TraceableTargetEnvironmentFunction that holds the stack trace of its creation.
 *
 * Unless it's intended to create hundreds of such objects a second, prefer using this class as a base for [TargetEnvironmentFunction]
 * to ease debugging in case of raised exceptions.
 */
@ApiStatus.Experimental
abstract class TraceableTargetEnvironmentFunction<R> : TargetEnvironmentFunction<R> {
  protected val creationStack: Throwable = Throwable("Creation stack")

  final override fun apply(t: TargetEnvironment): R =
    try {
      applyInner(t)
    }
    catch (err: Throwable) {
      err.addSuppressed(creationStack)
      throw err
    }

  abstract fun applyInner(t: TargetEnvironment): R

  override fun <V> andThen(after: Function<in R, out V>): TraceableTargetEnvironmentFunction<V> =
    TraceableTargetEnvironmentFunction { targetEnvironment ->
      after.apply(apply(targetEnvironment))
    }

  companion object {
    @JvmStatic
    inline operator fun <R> invoke(
      crossinline delegate: (targetEnvironment: TargetEnvironment) -> R,
    ): TraceableTargetEnvironmentFunction<R> =
      object : TraceableTargetEnvironmentFunction<R>() {
        override fun applyInner(t: TargetEnvironment): R = delegate(t)
      }
  }
}

/**
 * This function is preferable to use over function literals in Kotlin
 * (i.e. `TargetEnvironmentFunction { value }`) and lambdas in Java
 * (i.e. `ignored -> value`) because it is has more explicit [toString] which
 * results in clear variable descriptions during debugging and better logging
 * abilities.
 */
fun <T> constant(value: T): TargetEnvironmentFunction<T> = Constant(value)

private data class Constant<T>(private val value: T) : TraceableTargetEnvironmentFunction<T>() {
  override fun applyInner(t: TargetEnvironment): T = value
}

@JvmOverloads
fun <T> Iterable<TargetEnvironmentFunction<T>>.joinToStringFunction(separator: CharSequence,
                                                                    transform: ((T) -> CharSequence)? = null): TargetEnvironmentFunction<String> =
  JoinedStringTargetEnvironmentFunction(iterable = this, separator = separator, transform = transform)

/**
 * Equivalent to `.andThen { it.joinToString(separator = ", ", transform = String::toStringLiteral) }` while providing better string
 * representation.
 *
 * @see Function.andThen
 * @see joinToString
 */
@JvmOverloads
fun <T> TargetEnvironmentFunction<out Iterable<T>>.andThenJoinToString(separator: CharSequence,
                                                                       transform: ((T) -> CharSequence)? = null): TargetEnvironmentFunction<String> =
  AndThenJoinToStringTargetEnvironmentFunction(function = this, separator = separator, transform = transform)

@Deprecated("Do not use strings for local path",
            ReplaceWith("getTargetEnvironmentForLocalPath(Paths.get(localPath))", "java.nio.file.Paths"))
fun TargetEnvironmentRequest.getTargetEnvironmentValueForLocalPath(localPath: String): TargetEnvironmentFunction<String> = getTargetEnvironmentValueForLocalPath(
  Path.of(localPath))

/**
 * Consider using [targetPath] function, which does not throw an exception if [localPath] cannot be mapped to a target path during the
 * resolution against [TargetEnvironment].
 */
@Deprecated("Use the overloaded method that takes no `TargetEnvironmentRequest` as a receiver",
            ReplaceWith("getTargetEnvironmentValueForLocalPath(localPath)"))
fun TargetEnvironmentRequest.getTargetEnvironmentValueForLocalPath(localPath: Path): TargetEnvironmentFunction<String> {
  if (this is LocalTargetEnvironmentRequest) return constant(localPath.toString())
  return TraceableTargetEnvironmentFunction { targetEnvironment -> targetEnvironment.resolveLocalPath(localPath) }
}

@Deprecated("Do not use strings for local path",
            ReplaceWith("getTargetEnvironmentValueForLocalPath(Paths.get(localPath))", "java.nio.file.Paths"))
fun getTargetEnvironmentValueForLocalPath(localPath: String): TargetEnvironmentFunction<String> =
  getTargetEnvironmentValueForLocalPath(Path.of(localPath))

/**
 * Returns function [target,targetPath] that converts [localPath] to the targetPath on certain target.
 *
 * Consider using [targetPath] function, which does not throw an exception if [localPath] cannot be mapped to a target path during the
 * resolution against [TargetEnvironment].
 */
fun getTargetEnvironmentValueForLocalPath(localPath: Path): TargetEnvironmentFunction<String> {
  return TraceableTargetEnvironmentFunction { targetEnvironment ->
    when (targetEnvironment) {
      is LocalTargetEnvironment -> localPath.toString()
      else -> targetEnvironment.resolveLocalPath(localPath)
    }
  }
}

/**
 * Returns the function that tries to resolve [localPath] to the corresponding path on the target against [TargetEnvironment].
 *
 * Resolution takes into account [ExternallySynchronized] interface, which might be implemented by [TargetEnvironment], and the list of
 * upload volumes in [TargetEnvironment].
 *
 * If there are no suitable mappings found, the function returns a string representation of [localPath] local path.
 */
@ApiStatus.Experimental
fun targetPath(localPath: Path): TargetEnvironmentFunction<String> = TargetPathFunction(localPath)

private fun TargetEnvironment.resolveLocalPath(localPath: Path): String {
  if (this is ExternallySynchronized) {
    val pathForSynchronizedVolume = tryMapToSynchronizedVolume(localPath)
    if (pathForSynchronizedVolume != null) return pathForSynchronizedVolume
  }
  val (uploadRoot, relativePath) = request.getUploadRootForLocalPath(localPath) ?: throw IllegalArgumentException(
    "Local path \"$localPath\" is not registered within uploads in the request")
  val volume = uploadVolumes[uploadRoot]
               ?: throw IllegalStateException("Upload root \"$uploadRoot\" is expected to be created in the target environment")
  return joinPaths(volume.targetRoot, relativePath, targetPlatform)
}

private fun TargetEnvironment.getTargetPath(localPath: Path): String? {
  if (this is ExternallySynchronized) {
    val pathForSynchronizedVolume = tryMapToSynchronizedVolume(localPath)
    if (pathForSynchronizedVolume != null) return pathForSynchronizedVolume
  }
  val (uploadRoot, relativePath) = request.getUploadRootForLocalPath(localPath) ?: return null
  val volume = uploadVolumes[uploadRoot]
               ?: throw IllegalStateException("Upload root \"$uploadRoot\" is expected to be created in the target environment")
  return joinPaths(volume.targetRoot, relativePath, targetPlatform)
}

private fun ExternallySynchronized.tryMapToSynchronizedVolume(localPath: Path): FullPathOnTarget? {
  // TODO [targets] Does not look nice
  this as TargetEnvironment
  return synchronizedVolumes.tryMapToSynchronizedVolume(localPath, targetPlatform)
}

fun List<TargetEnvironment.SynchronizedVolume>.tryMapToSynchronizedVolume(localPath: Path, targetPlatform: TargetPlatform): FullPathOnTarget? {
  val (volume, relativePath) = findRemotePathByMapping(this, localPath, targetPlatform) ?: return null
  return joinPaths(volume.targetPath, relativePath, targetPlatform)
}

@Deprecated("Do not use strings for local path", ReplaceWith("getUploadRootForLocalPath(Paths.get(localPath))", "java.nio.file.Paths"))
fun TargetEnvironmentRequest.getUploadRootForLocalPath(localPath: String): Pair<TargetEnvironment.UploadRoot, String>? =
  getUploadRootForLocalPath(Paths.get(localPath))

fun TargetEnvironmentRequest.getUploadRootForLocalPath(localPath: Path): Pair<TargetEnvironment.UploadRoot, String>? =
  findRemotePathByMapping(uploadVolumes, localPath, targetPlatform)

/**
 * If [localPath] could be mapped to the remote system by one of the [mappings], return
 * both: mapping and mapped path
 */
private fun <T> findRemotePathByMapping(mappings: Collection<T>, localPath: Path, target: TargetPlatform): Pair<T, FullPathOnTarget>?
  where T : TargetEnvironment.MappingWithLocalPath = mappings.firstNotNullOfOrNull { uploadRoot ->
  val targetFileSep = target.platform.fileSeparator
  getRelativePathIfAncestor(ancestor = uploadRoot.localRootPath, file = localPath)?.let { relativePath ->
    uploadRoot to if (File.separatorChar != targetFileSep) relativePath.replace(File.separatorChar, targetFileSep) else relativePath
  }
}


private fun getRelativePathIfAncestor(ancestor: Path, file: Path): String? =
  try {
    ancestor.relativize(file).takeIf { !it.startsWith("..") }?.toString()
  }
  catch (ignored: InvalidPathException) {
    null
  }
  catch (ignored: IllegalArgumentException) {
    // It should not happen, but some tests use relative paths, and they fail trying to call `Paths.get("\\").relativize(Paths.get("."))`
    null
  }

private fun joinPaths(basePath: String, relativePath: String, targetPlatform: TargetPlatform): String {
  if (relativePath == ".") {
    return basePath
  }

  val fileSeparator = targetPlatform.platform.fileSeparator.toString()
  return FileUtil.toSystemIndependentName("${basePath.removeSuffix(fileSeparator)}$fileSeparator$relativePath")
}

fun TargetEnvironment.UploadRoot.getTargetUploadPath(): TargetEnvironmentFunction<String> =
  TraceableTargetEnvironmentFunction { targetEnvironment ->
    val uploadRoot = this@getTargetUploadPath
    val uploadableVolume = targetEnvironment.uploadVolumes[uploadRoot]
                           ?: throw IllegalStateException("Upload root \"$uploadRoot\" cannot be found")
    uploadableVolume.targetRoot
  }

fun TargetEnvironmentFunction<String>.getRelativeTargetPath(targetRelativePath: String): TargetEnvironmentFunction<String> =
  TraceableTargetEnvironmentFunction { targetEnvironment ->
    val targetBasePath = this@getRelativeTargetPath.apply(targetEnvironment)
    joinPaths(targetBasePath, targetRelativePath, targetEnvironment.targetPlatform)
  }

fun TargetEnvironment.DownloadRoot.getTargetDownloadPath(): TargetEnvironmentFunction<String> =
  TraceableTargetEnvironmentFunction { targetEnvironment ->
    val downloadRoot = this@getTargetDownloadPath
    val downloadableVolume = targetEnvironment.downloadVolumes[downloadRoot]
                             ?: throw IllegalStateException("Download root \"$downloadRoot\" cannot be found")
    downloadableVolume.targetRoot
  }

fun TargetEnvironment.LocalPortBinding.getTargetEnvironmentValue(): TargetEnvironmentFunction<HostPort> =
  TraceableTargetEnvironmentFunction { targetEnvironment ->
    val localPortBinding = this@getTargetEnvironmentValue
    val resolvedPortBinding = (targetEnvironment.localPortBindings[localPortBinding]
                               ?: throw IllegalStateException("Local port binding \"$localPortBinding\" cannot be found"))
    resolvedPortBinding.targetEndpoint
  }

@Throws(IOException::class)
fun TargetEnvironment.downloadFromTarget(localPath: Path, progressIndicator: ProgressIndicator) {
  val localFileDir = localPath.parent
  val downloadVolumes = downloadVolumes.values
  val downloadVolume = downloadVolumes.find { it.localRoot == localFileDir }
                       ?: error("Volume with local root $localFileDir not found")
  downloadVolume.download(localPath.name, progressIndicator)
}

/**
 * The function that tries to resolve [localPath] to the corresponding path on the target against [TargetEnvironment].
 *
 * Resolution takes into account [ExternallySynchronized] interface, which might be implemented by [TargetEnvironment], and the list of
 * upload volumes in [TargetEnvironment].
 *
 * If there are no suitable mappings found, the function returns a string representation of [localPath].
 */
private data class TargetPathFunction(private val localPath: Path) : TraceableTargetEnvironmentFunction<String>() {
  override fun applyInner(t: TargetEnvironment): String {
    if (t is LocalTargetEnvironment) {
      return localPath.toString()
    }
    val targetPath = t.getTargetPath(localPath)
    if (targetPath == null) {
      LOG.error("Could not find a target path for the local path $localPath requested at:", creationStack)
      return localPath.toString()
    }
    return targetPath
  }

  companion object {
    private val LOG = logger<TargetPathFunction>()
  }
}

private class JoinedStringTargetEnvironmentFunction<T>(private val iterable: Iterable<TargetEnvironmentFunction<T>>,
                                                       private val separator: CharSequence,
                                                       private val transform: ((T) -> CharSequence)?)
  : TraceableTargetEnvironmentFunction<String>() {
  override fun applyInner(t: TargetEnvironment): String = iterable.map { it.apply(t) }.joinToString(separator = separator,
                                                                                                    transform = transform)

  override fun toString(): String {
    return "JoinedStringTargetEnvironmentValue(iterable=$iterable, separator=$separator, transform=$transform)"
  }
}

private class AndThenJoinToStringTargetEnvironmentFunction<T>(private val function: TargetEnvironmentFunction<out Iterable<T>>,
                                                              private val separator: CharSequence,
                                                              private val transform: ((T) -> CharSequence)?)
  : TraceableTargetEnvironmentFunction<String>() {
  override fun applyInner(t: TargetEnvironment): String = function.apply(t).joinToString(separator = separator, transform = transform)

  override fun toString(): String =
    "AndThenJoinToStringTargetEnvironmentFunction(function=$function, separator=$separator, transform=$transform)"
}

private class ConcatTargetEnvironmentFunction(private val left: TargetEnvironmentFunction<String>,
                                              private val right: TargetEnvironmentFunction<String>)
  : TraceableTargetEnvironmentFunction<String>() {
  override fun applyInner(t: TargetEnvironment): String = left.apply(t) + right.apply(t)

  override fun toString(): String = "ConcatTargetEnvironmentFunction(left=$left, right=$right)"
}

operator fun TargetEnvironmentFunction<String>.plus(f: TargetEnvironmentFunction<String>): TargetEnvironmentFunction<String> =
  ConcatTargetEnvironmentFunction(this, f)

operator fun TargetEnvironmentFunction<String>.plus(str: String): TargetEnvironmentFunction<String> = this + constant(str)

fun <T> Iterable<TargetEnvironmentFunction<T>>.toLinkedSetFunction(): TargetEnvironmentFunction<Set<T>> =
  LinkedSetTargetEnvironmentFunction(this)

private class LinkedSetTargetEnvironmentFunction<T>(private val iterable: Iterable<TargetEnvironmentFunction<T>>)
  : TraceableTargetEnvironmentFunction<Set<T>>() {
  override fun applyInner(t: TargetEnvironment): Set<T> = iterable.mapTo(linkedSetOf()) { it.apply(t) }

  override fun toString(): String = "LinkedSetTargetEnvironmentFunction(iterable=$iterable)"
}