// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.projectFilter

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.util.indexing.IdFilter
import com.intellij.util.indexing.dependencies.ProjectIndexingDependenciesService
import java.util.concurrent.atomic.AtomicReference

internal abstract class ProjectIndexableFilesFilterFactory {
  abstract fun create(project: Project): ProjectIndexableFilesFilter
  fun createHealthCheck(project: Project, filter: ProjectIndexableFilesFilter): ProjectIndexableFilesFilterHealthCheck {
    return ProjectIndexableFilesFilterHealthCheck(project, filter)
  }
}

internal abstract class ProjectIndexableFilesFilter(protected val project: Project, val checkAllExpectedIndexableFilesDuringHealthcheck: Boolean) : IdFilter() {
  private val parallelUpdatesCounter = AtomicVersionedCounter()

  override fun getFilteringScopeType(): FilterScopeType = FilterScopeType.PROJECT_AND_LIBRARIES

  abstract fun ensureFileIdPresent(fileId: Int, add: () -> Boolean): Boolean
  abstract fun removeFileId(fileId: Int)
  abstract fun resetFileIds()
  open fun onProjectClosing(project: Project) = Unit

  /**
   * This is a temp method
   */
  open val wasDataLoadedFromDisk: Boolean = false

  protected fun <T> runUpdate(action: () -> T): T {
    parallelUpdatesCounter.update(1)
    try {
      return action()
    }
    finally {
      parallelUpdatesCounter.update(-1)
    }
  }

  fun <T> runAndCheckThatNoChangesHappened(action: () -> T): T {
    val (numberOfParallelUpdates, version) = parallelUpdatesCounter.getCounterAndVersion()
    if (numberOfParallelUpdates != 0) throw ProcessCanceledException()
    val res = action()
    val (numberOfParallelUpdates2, version2) = parallelUpdatesCounter.getCounterAndVersion()
    if (numberOfParallelUpdates2 != 0 || version2 != version) {
      throw ProcessCanceledException()
    }
    return res
  }

  abstract fun getFileStatuses(): Sequence<Pair<Int, Boolean>>
}

private class AtomicVersionedCounter {
  private val counterAndVersion = AtomicReference(0 to 0)

  fun update(counterUpdate: Int) {
    var pair = counterAndVersion.get()
    while (!counterAndVersion.compareAndSet(pair, pair.first + counterUpdate to pair.second + 1)) {
      pair = counterAndVersion.get()
    }
  }

  fun getCounterAndVersion(): Pair<Int, Int> = counterAndVersion.get()
}

internal fun <T> runIfScanningScanningIsCompleted(project: Project, action: () -> T): T {
  val service = project.getService(ProjectIndexingDependenciesService::class.java)
  if (!service.isScanningCompleted()) throw ProcessCanceledException()
  val res = action()
  if (!service.isScanningCompleted()) throw ProcessCanceledException()
  return res
}