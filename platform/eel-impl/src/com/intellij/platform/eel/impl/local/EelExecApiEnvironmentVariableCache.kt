// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.local

import com.intellij.platform.eel.EelExecApi
import com.intellij.platform.eel.EelExecApi.EnvironmentVariablesDeferred
import com.intellij.platform.eel.EelExecPosixApi
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.jetbrains.annotations.ApiStatus
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

/**
 * [makeEnvironmentVariablesDeferred] MUST return deferreds with `CoroutineStart.LAZY`.
 */
@ApiStatus.Internal
class EelExecApiEnvironmentVariableCache(
  private val makeEnvironmentVariablesDeferred: (EelExecPosixApi.PosixEnvironmentVariablesOptions.Mode?) -> Deferred<Map<String, String>>,
) {
  /**
   * If the first feature is present, it is already completed successfully.
   * The second feature can be in any state.
   */
  @JvmInline
  private value class EnvVarCache(private val pair: Pair<Deferred<Map<String, String>>?, Deferred<Map<String, String>>>) {
    val envVarsInProgress: Deferred<Map<String, String>> get() = pair.second

    @OptIn(ExperimentalCoroutinesApi::class)
    val latestKnownEnvVars: Deferred<Map<String, String>>?
      get() =
        if (envVarsInProgress.isCompleted && envVarsInProgress.getCompletionExceptionOrNull() == null) {
          envVarsInProgress
        }
        else {
          pair.first
        }
  }

  private val environmentVariablesCache = ConcurrentHashMap<Optional<EelExecPosixApi.PosixEnvironmentVariablesOptions.Mode>, EnvVarCache>()

  fun getDeferred(
    mode: EelExecPosixApi.PosixEnvironmentVariablesOptions.Mode?,
    opts: EelExecApi.EnvironmentVariablesOptions
  ): EnvironmentVariablesDeferred {
    val cacheKey = Optional.ofNullable(mode)
    var newEnvVarCache: EnvVarCache

    do {
      val envVarCache = environmentVariablesCache[cacheKey]
      val successfullyUpdated: Boolean

      if (envVarCache == null) {
        newEnvVarCache = EnvVarCache(null to makeEnvironmentVariablesDeferred(mode))
        successfullyUpdated = environmentVariablesCache.putIfAbsent(cacheKey, newEnvVarCache) == null
      }
      else {
        if (opts.onlyActual && envVarCache.envVarsInProgress.isActive) {
          return EnvironmentVariablesDeferred(envVarCache.envVarsInProgress)
        }

        val latestKnownEnvVars = envVarCache.latestKnownEnvVars
        if (!opts.onlyActual && latestKnownEnvVars != null) {
          return EnvironmentVariablesDeferred(latestKnownEnvVars)
        }

        newEnvVarCache = EnvVarCache(latestKnownEnvVars to makeEnvironmentVariablesDeferred(mode))
        successfullyUpdated = environmentVariablesCache.replace(cacheKey, envVarCache, newEnvVarCache)
      }
    }
    while (!successfullyUpdated)

    newEnvVarCache.envVarsInProgress.start()
    return EnvironmentVariablesDeferred(newEnvVarCache.envVarsInProgress)
  }

  fun clear() {
    environmentVariablesCache.clear()
  }
}