// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("TargetEnvironmentFunctions")

package com.intellij.execution.target.value

import com.intellij.execution.target.*
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest
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
  private val creationStack: Throwable = Throwable("Creation stack")

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

private class Constant<T>(private val value: T) : TraceableTargetEnvironmentFunction<T>() {
  override fun toString(): String = "${javaClass.simpleName}($value)"
  override fun applyInner(t: TargetEnvironment): T = value
}

fun <T> Iterable<TargetEnvironmentFunction<T>>.joinToStringFunction(separator: CharSequence): TargetEnvironmentFunction<String> =
  JoinedStringTargetEnvironmentFunction(iterable = this, separator = separator)

fun TargetEnvironmentRequest.getTargetEnvironmentValueForLocalPath(localPath: String): TargetEnvironmentFunction<String> {
  if (this is LocalTargetEnvironmentRequest) return constant(localPath)
  return TraceableTargetEnvironmentFunction { targetEnvironment ->
    if (targetEnvironment is ExternallySynchronized) {
      val pathForSynchronizedVolume = targetEnvironment.tryMapToSynchronizedVolume(localPath)
      if (pathForSynchronizedVolume != null) return@TraceableTargetEnvironmentFunction pathForSynchronizedVolume
    }
    val (uploadRoot, relativePath) = getUploadRootForLocalPath(localPath) ?: throw IllegalArgumentException(
      "Local path \"$localPath\" is not registered within uploads in the request")
    val volume = targetEnvironment.uploadVolumes[uploadRoot]
                 ?: throw IllegalStateException("Upload root \"$uploadRoot\" is expected to be created in the target environment")
    joinPaths(volume.targetRoot, relativePath, targetEnvironment.targetPlatform)
  }
}

private fun ExternallySynchronized.tryMapToSynchronizedVolume(localPath: String): String? {
  // TODO [targets] Does not look nice
  this as TargetEnvironment
  val targetFileSeparator = targetPlatform.platform.fileSeparator
  val (volume, relativePath) = synchronizedVolumes.firstNotNullOfOrNull { volume ->
    getRelativePathIfAncestor(ancestor = volume.localPath, file = localPath)?.let { relativePath ->
      volume to if (File.separatorChar != targetFileSeparator) {
        relativePath.replace(File.separatorChar, targetFileSeparator)
      }
      else {
        relativePath
      }
    }
  } ?: return null
  return joinPaths(volume.targetPath, relativePath, targetPlatform)
}

fun TargetEnvironmentRequest.getUploadRootForLocalPath(localPath: String): Pair<TargetEnvironment.UploadRoot, String>? {
  val targetFileSeparator = targetPlatform.platform.fileSeparator
  return uploadVolumes.mapNotNull { uploadRoot ->
    getRelativePathIfAncestor(ancestor = uploadRoot.localRootPath, file = localPath)?.let { relativePath ->
      uploadRoot to if (File.separatorChar != targetFileSeparator) {
        relativePath.replace(File.separatorChar, targetFileSeparator)
      }
      else {
        relativePath
      }
    }
  }.firstOrNull()
}

private fun getRelativePathIfAncestor(ancestor: Path, file: String): String? =
  try {
    ancestor.relativize(Paths.get(file)).takeIf { !it.startsWith("..") }?.toString()
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

private class JoinedStringTargetEnvironmentFunction<T>(private val iterable: Iterable<TargetEnvironmentFunction<T>>,
                                                       private val separator: CharSequence) : TraceableTargetEnvironmentFunction<String>() {
  override fun applyInner(t: TargetEnvironment): String = iterable.map { it.apply(t) }.joinToString(separator = separator)

  override fun toString(): String {
    return "JoinedStringTargetEnvironmentValue(iterable=$iterable, separator=$separator)"
  }
}

private class ConcatTargetEnvironmentFunction(private val left: TargetEnvironmentFunction<String>,
                                              private val right: TargetEnvironmentFunction<String>)
  : TraceableTargetEnvironmentFunction<String>() {
  override fun applyInner(t: TargetEnvironment): String = left.apply(t) + right.apply(t)

  override fun toString(): String {
    return "ConcatTargetEnvironmentFunction(left=$left, right=$right)"
  }
}

operator fun TargetEnvironmentFunction<String>.plus(f: TargetEnvironmentFunction<String>): TargetEnvironmentFunction<String> =
  ConcatTargetEnvironmentFunction(this, f)

operator fun TargetEnvironmentFunction<String>.plus(str: String): TargetEnvironmentFunction<String> = this + constant(str)