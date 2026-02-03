// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target.java

import com.intellij.execution.target.value.TargetValue
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
sealed class TargetPaths {

  companion object {
    /**
     * Build paths that do not depend on each other in resolved forms.
     */
    @JvmStatic
    fun unordered(uploadPaths: Set<String> = emptySet(), downloadPaths: Set<String> = emptySet()): TargetPaths {
      return Unordered(uploadPaths, downloadPaths)
    }

    /**
     * Build paths that can depend on each other in resolved form..
     * For example, one may use some resolved path to build another path,
     * or to write in in a file that must be uploaded to target.
     */
    @JvmStatic
    fun ordered(buildOrder: OrderBuilder.() -> Unit): TargetPaths {
      val builder = OrderBuilder()
      builder.buildOrder()
      return builder.build()
    }
  }

  internal var resolvedPaths: Map<TargetPath, TargetValue<String>>? = null

  internal abstract fun resolveAll(uploadPathsResolver: (TargetPath) -> TargetValue<String>,
                                   downloadPathsResolver: (TargetPath) -> TargetValue<String>)

  internal fun getResolved(localValue: String): TargetValue<String> = resolvedPaths?.entries?.find { localValue == it.key.localPath }?.value
                                                                      ?: throw IllegalStateException("Path $localValue is not resolved")

  class OrderBuilder {
    private val orderedPaths = mutableListOf<TargetPath>()

    /**
     * Adds a path that must be uploaded, with related actions that must be performed before and after this path is resolved.
     */
    fun upload(localPath: String, beforeUploadResolved: (String) -> Unit = {}, afterUploadResolved: (String) -> Unit): OrderBuilder = apply {
      orderedPaths += TargetPath.toUpload(localPath, beforeUploadResolved, afterUploadResolved)
    }

    /**
     * Adds a path that must be downloaded, with related actions that must be performed before and after this path is resolved.
     */
    fun download(localPath: String, beforeDownloadResolved: (String) -> Unit = {}, afterDownloadResolved: (String) -> Unit): OrderBuilder = apply {
      orderedPaths += TargetPath.toDownload(localPath, beforeDownloadResolved, afterDownloadResolved)
    }

    fun build(): TargetPaths = Ordered(orderedPaths)
  }

  private class Unordered(val uploadPaths: Set<String> = emptySet(),
                          val downloadPaths: Set<String> = emptySet()) : TargetPaths() {

    override fun resolveAll(uploadPathsResolver: (TargetPath) -> TargetValue<String>,
                            downloadPathsResolver: (TargetPath) -> TargetValue<String>) {
      resolvedPaths = uploadPaths.map { TargetPath.toUpload(it) }.associateWith(uploadPathsResolver) +
                      downloadPaths.map { TargetPath.toDownload(it) }.associateWith(downloadPathsResolver)
    }
  }

  private class Ordered(val paths: Iterable<TargetPath>) : TargetPaths() {

    override fun resolveAll(uploadPathsResolver: (TargetPath) -> TargetValue<String>,
                            downloadPathsResolver: (TargetPath) -> TargetValue<String>) {
      resolvedPaths = paths.associateWith { it.resolve(uploadPathsResolver, downloadPathsResolver) }
    }
  }
}

@ApiStatus.Experimental
class TargetPath private constructor(val localPath: String,
                                     private val kind: PathKind,
                                     val beforeUploadOrDownloadResolved: (String) -> Unit = {},
                                     val afterUploadOrDownloadResolved: (String) -> Unit = {}) {

  fun toLocalPath(): TargetValue<String> {
    beforeUploadOrDownloadResolved(localPath)
    afterUploadOrDownloadResolved(localPath)
    return TargetValue.fixed(localPath)
  }

  internal fun resolve(uploadPathsResolver: (TargetPath) -> TargetValue<String>,
                       downloadPathsResolver: (TargetPath) -> TargetValue<String>): TargetValue<String> {
    return when (kind) {
      PathKind.UPLOAD -> uploadPathsResolver(this)
      PathKind.DOWNLOAD -> downloadPathsResolver(this)
    }
  }

  private enum class PathKind { UPLOAD, DOWNLOAD }

  companion object {
    fun toUpload(path: String, beforeUploadResolved: (String) -> Unit = {}, afterUploadResolved: (String) -> Unit = {}): TargetPath =
      TargetPath(path, PathKind.UPLOAD, beforeUploadResolved, afterUploadResolved)

    fun toDownload(path: String, beforeDownloadResolved: (String) -> Unit = {}, afterDownloadResolved: (String) -> Unit = {}): TargetPath =
      TargetPath(path, PathKind.DOWNLOAD, beforeDownloadResolved, afterDownloadResolved)
  }
}


