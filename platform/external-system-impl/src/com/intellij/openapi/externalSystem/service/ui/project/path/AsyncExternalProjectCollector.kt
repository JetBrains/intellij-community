// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.ui.project.path

import com.intellij.openapi.externalSystem.service.ui.completion.cache.AsyncLocalCache
import com.intellij.openapi.util.ModificationTracker
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
internal abstract class AsyncExternalProjectCollector private constructor() {

  protected abstract val modificationTracker: ModificationTracker

  protected abstract suspend fun collectExternalProjects(): List<ExternalProject>

  private val externalProjectCache = AsyncLocalCache<List<ExternalProject>>()

  suspend fun getOrCollectExternalProjects(): List<ExternalProject> {
    val modificationCount = modificationTracker.modificationCount
    return externalProjectCache.getOrCreateValue(modificationCount) {
      collectExternalProjects()
    }
  }

  companion object {

    fun create(
      modificationTracker: ModificationTracker,
      collect: suspend () -> List<ExternalProject>
    ): AsyncExternalProjectCollector {
      return object : AsyncExternalProjectCollector() {
        override val modificationTracker = modificationTracker
        override suspend fun collectExternalProjects() = collect()
      }
    }
  }
}