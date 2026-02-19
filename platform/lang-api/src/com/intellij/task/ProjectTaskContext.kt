// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.task

import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.MultiMap
import org.jetbrains.annotations.ApiStatus
import java.util.Optional
import kotlin.concurrent.Volatile

/**
 * @author Vladislav.Soroka
 */
open class ProjectTaskContext @JvmOverloads constructor(
  val sessionId: Any? = null,
  val runConfiguration: RunConfiguration? = null,
  /**
   * true indicates that the task was started automatically, e.g. resources compilation on frame deactivation
   */
  val isAutoRun: Boolean = false,
  val dataContext: DataContext? = null,
) : UserDataHolderBase() {

  private val myGeneratedFiles: MultiMap<String, String> = MultiMap.createConcurrentSet()
  private val myDirtyOutputPaths: MutableList<() -> Collection<String>> = ContainerUtil.createConcurrentList()

  constructor(autoRun: Boolean) : this(null, null, autoRun)

  @get:ApiStatus.Experimental
  @Volatile
  var isCollectionOfGeneratedFilesEnabled: Boolean = false

  /**
   * The class that initiated the task execution.
   * This is an exposed version of [com.intellij.task.impl.ProjectTaskManagerImpl.BUILD_ORIGINATOR_KEY]
   */
  @ApiStatus.Internal
  @Volatile
  var buildOriginatorClass: Class<*>? = null

  @get:ApiStatus.Experimental
  val generatedFilesRoots: Collection<String>
    /**
     * Returns roots of the files generated during the task session.
     * Note, generated files collecting is disabled by default.
     * It can be requested using the [.enableCollectionOfGeneratedFiles] method by the task initiator, see [ProjectTaskManager.run].
     * Or using the [ProjectTaskListener.started] event.
     */
    get() = myGeneratedFiles.keySet()

  /**
   * Returns files generated during the task session in the specified root.
   * Note, generated files collecting is disabled by default.
   * It can be requested using the [.enableCollectionOfGeneratedFiles] method by the task initiator, see [ProjectTaskManager.run].
   * Or using the [ProjectTaskListener.started] event.
   */
  @ApiStatus.Experimental
  fun getGeneratedFilesRelativePaths(root: String): Collection<String?> {
    return myGeneratedFiles.get(root)
  }

  /**
   * This method isn't supposed to be used directly.
   * [ProjectTaskRunner]s can use it to report information about generated files during some task execution.
   * 
   * @param root the root directory of the generated file
   * @param relativePath the generated file relative path with regard to the root
   */
  @ApiStatus.Experimental
  fun fileGenerated(root: String, relativePath: String) {
    if (isCollectionOfGeneratedFilesEnabled) {
      myGeneratedFiles.putValue(root, relativePath)
    }
  }

  /**
   * This method isn't supposed to be used directly.
   * [ProjectTaskRunner]s can use it to report output paths of generated files produced during some task execution.
   * 
   * 
   * Note, generated files collecting is disabled by default.
   * It can be requested using the [.enableCollectionOfGeneratedFiles] method by the task initiator, see [ProjectTaskManager.run].
   * Or using the [ProjectTaskListener.started] event.
   * 
   * 
   * The method should be used ONLY if the [ProjectTaskRunner] doesn't support [.fileGenerated] events.
   */
  @ApiStatus.Experimental
  fun addDirtyOutputPathsProvider(outputPathsProvider: () -> Collection<String>) {
    if (isCollectionOfGeneratedFilesEnabled) {
      myDirtyOutputPaths.add(outputPathsProvider)
    }
  }

  val dirtyOutputPaths: Optional<List<String>>
    /**
     * Provides output paths that can be used for generated files by some tasks.
     * The intended usage is to scan those directories for modified files.
     * 
     * 
     * Can be useful for [ProjectTaskRunner]s which doesn't support [.fileGenerated] events.
     */
    get() = if (myDirtyOutputPaths.isEmpty()) Optional.empty<List<String>>()
    else Optional.of(myDirtyOutputPaths
                       .map { supplier -> supplier() }
                       .flatten()
                       .distinct()
    )

  fun <T> withUserData(key: Key<T>, value: T?): ProjectTaskContext {
    putUserData(key, value)
    return this
  }
}
