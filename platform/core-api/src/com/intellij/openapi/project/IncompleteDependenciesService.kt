// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import com.intellij.openapi.application.AccessToken
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus


/**
 * Indicates project state when dependencies are not yet downloaded.
 */
@ApiStatus.Internal
interface IncompleteDependenciesService {
  /**
   * Note that [Flow.collect] is invoked asynchronously outside any lock
   * and there is no guarantee that state was not yet changed or will not change
   */
  val stateFlow: Flow<DependenciesState>

  @RequiresReadLock
  fun getState(): DependenciesState

  @RequiresWriteLock
  fun enterIncompleteState(): IncompleteDependenciesAccessToken

  enum class DependenciesState(val isComplete: Boolean) {
    COMPLETE(true),
    INCOMPLETE(false)
  }

  abstract class IncompleteDependenciesAccessToken : AccessToken() {
    @RequiresWriteLock
    abstract override fun finish()
  }
}
