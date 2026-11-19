// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target.java

import com.intellij.execution.target.value.TargetValue
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import java.util.concurrent.atomic.AtomicBoolean

@ApiStatus.Experimental
sealed class TargetPaths(private val paths: List<TargetPath>) {

  companion object {
    /**
     * Build paths that do not depend on each other in resolved forms.
     *
     * @throws IllegalArgumentException if a path is in both [uploadPaths] and [downloadPaths].
     */
    @JvmStatic
    fun unordered(uploadPaths: Set<String> = emptySet(), downloadPaths: Set<String> = emptySet()): TargetPaths {
      return Unordered(uploadPaths, downloadPaths)
    }

    /**
     * Build paths that can depend on each other in resolved form.
     * For example, one may use some resolved path to build another path,
     * or to write in a file that must be uploaded to target.
     *
     * @throws IllegalArgumentException if [buildOrder] adds a local path more than once.
     */
    @JvmStatic
    fun ordered(buildOrder: OrderBuilder.() -> Unit): TargetPaths {
      val builder = OrderBuilder()
      builder.buildOrder()
      return builder.build()
    }
  }

  private val slots: Map<String, AsyncPromise<TargetValue<String>>> = paths.associate { it.localPath to AsyncPromise() }
  private val resolutionStarted = AtomicBoolean()

  init {
    require(slots.size == paths.size) { "Duplicate local paths: ${paths.map { it.localPath }}" }
  }

  /**
   * Returns a value for [localPath]. The value completes after [resolveAll] resolves the path.
   */
  internal fun resolveAsync(localPath: String): TargetValue<String> {
    val slot = slots[localPath] ?: throw IllegalArgumentException("Path $localPath is not declared")
    return PendingTargetValue(slot)
  }

  /**
   * Resolves all paths in the declaration order. Call it once.
   */
  internal fun resolveAll(uploadPathsResolver: (TargetPath) -> TargetValue<String>,
                          downloadPathsResolver: (TargetPath) -> TargetValue<String>) {
    check(resolutionStarted.compareAndSet(false, true)) { "Paths are already resolved" }
    for (path in paths) {
      slots.getValue(path.localPath).setResult(path.resolve(uploadPathsResolver, downloadPathsResolver))
    }
  }

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

    fun build(): TargetPaths = Ordered(orderedPaths.toList())
  }

  private class Unordered(uploadPaths: Set<String>, downloadPaths: Set<String>)
    : TargetPaths(uploadPaths.map { TargetPath.toUpload(it) } + downloadPaths.map { TargetPath.toDownload(it) })

  private class Ordered(paths: List<TargetPath>) : TargetPaths(paths)
}

private class PendingTargetValue(private val slot: Promise<TargetValue<String>>) : TargetValue<String> {
  override fun getLocalValue(): Promise<String> = slot.thenAsync { it.localValue }

  override fun getTargetValue(): Promise<String> = slot.thenAsync { it.targetValue }
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


