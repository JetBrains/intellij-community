// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("TargetEnvironmentFunctions")

package com.intellij.execution.target.value

import com.intellij.execution.target.HostPort
import com.intellij.execution.target.TargetEnvironment
import com.intellij.execution.target.TargetEnvironmentRequest
import com.intellij.execution.target.TargetPlatform
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.io.FileUtil
import java.io.File
import java.io.IOException
import java.nio.file.Path
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
 */
typealias TargetEnvironmentFunction<R> = Function<TargetEnvironment, R>

/**
 * This function is preferable to use over function literals in Kotlin
 * (i.e. `TargetEnvironmentFunction { value }`) and lambdas in Java
 * (i.e. `ignored -> value`) because it is has more explicit [toString] which
 * results in clear variable descriptions during debugging and better logging
 * abilities.
 */
fun <T> constant(value: T): TargetEnvironmentFunction<T> = TargetEnvironmentFunction { value }

fun <T> Iterable<TargetEnvironmentFunction<T>>.joinToStringFunction(separator: CharSequence): TargetEnvironmentFunction<String> =
  JoinedStringTargetEnvironmentFunction(iterable = this, separator = separator)

fun TargetEnvironmentRequest.getTargetEnvironmentValueForLocalPath(localPath: String): TargetEnvironmentFunction<String> {
  val (uploadRoot, relativePath) = getUploadRootForLocalPath(localPath)
                             ?: throw IllegalArgumentException("Local path \"$localPath\" is not registered within uploads in the request")
  return TargetEnvironmentFunction { targetEnvironment ->
    val volume = targetEnvironment.uploadVolumes[uploadRoot]
                 ?: throw IllegalStateException("Upload root \"$uploadRoot\" is expected to be created in the target environment")
    return@TargetEnvironmentFunction joinPaths(volume.targetRoot, relativePath, targetEnvironment.targetPlatform)
  }
}

fun TargetEnvironmentRequest.getUploadRootForLocalPath(localPath: String): Pair<TargetEnvironment.UploadRoot, String>? {
  val targetFileSeparator = targetPlatform.platform.fileSeparator
  return uploadVolumes.mapNotNull { uploadRoot ->
    getRelativePathIfAncestor(ancestor = uploadRoot.localRootPath.toString(), file = localPath)?.let { relativePath ->
      uploadRoot to if (File.separatorChar != targetFileSeparator) {
        relativePath.replace(File.separatorChar, targetFileSeparator)
      }
      else {
        relativePath
      }
    }
  }.firstOrNull()
}

private fun getRelativePathIfAncestor(ancestor: String, file: String): String? =
  if (FileUtil.isAncestor(ancestor, file, false)) {
    FileUtil.getRelativePath(ancestor, file, File.separatorChar)
  }
  else {
    null
  }

private fun joinPaths(basePath: String, relativePath: String, targetPlatform: TargetPlatform): String {
  val fileSeparator = targetPlatform.platform.fileSeparator.toString()
  return FileUtil.toSystemIndependentName("${basePath.removeSuffix(fileSeparator)}$fileSeparator$relativePath")
}

fun TargetEnvironment.UploadRoot.getTargetUploadPath(): TargetEnvironmentFunction<String> =
  TargetEnvironmentFunction { targetEnvironment ->
    val uploadRoot = this@getTargetUploadPath
    val uploadableVolume = targetEnvironment.uploadVolumes[uploadRoot]
                           ?: throw IllegalStateException("Upload root \"$uploadRoot\" cannot be found")
    return@TargetEnvironmentFunction uploadableVolume.targetRoot
  }

fun TargetEnvironmentFunction<String>.getRelativeTargetPath(targetRelativePath: String): TargetEnvironmentFunction<String> =
  TargetEnvironmentFunction { targetEnvironment ->
    val targetBasePath = this@getRelativeTargetPath.apply(targetEnvironment)
    return@TargetEnvironmentFunction joinPaths(targetBasePath, targetRelativePath, targetEnvironment.targetPlatform)
  }

fun TargetEnvironment.DownloadRoot.getTargetDownloadPath(): TargetEnvironmentFunction<String> =
  TargetEnvironmentFunction { targetEnvironment ->
    val downloadRoot = this@getTargetDownloadPath
    val downloadableVolume = targetEnvironment.downloadVolumes[downloadRoot]
                             ?: throw IllegalStateException("Download root \"$downloadRoot\" cannot be found")
    return@TargetEnvironmentFunction downloadableVolume.targetRoot
  }

fun TargetEnvironment.LocalPortBinding.getTargetEnvironmentValue(): TargetEnvironmentFunction<HostPort> =
  TargetEnvironmentFunction { targetEnvironment ->
    val localPortBinding = this@getTargetEnvironmentValue
    val resolvedPortBinding = (targetEnvironment.localPortBindings[localPortBinding]
                               ?: throw IllegalStateException("Local port binding \"$localPortBinding\" cannot be found"))
    return@TargetEnvironmentFunction resolvedPortBinding.targetEndpoint
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
                                                       private val separator: CharSequence) : TargetEnvironmentFunction<String> {
  override fun apply(t: TargetEnvironment): String = iterable.map { it.apply(t) }.joinToString(separator = separator)

  override fun toString(): String {
    return "JoinedStringTargetEnvironmentValue(iterable=$iterable, separator=$separator)"
  }
}
