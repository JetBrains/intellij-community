// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.eel

import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelExecApi
import org.jetbrains.annotations.ApiStatus.Internal

/**
 * A decorator for [EelApi] that normalizes paths within process builder arguments and environment variables.
 *
 * This class adds the capability to automatically convert and normalize paths prefixed with a specified string.
 * If system independence of paths is required, it converts paths to a system-independent format using forward slashes.
 */
@Internal
class EelApiWithPathsNormalization(
  private val prefix: String,
  private val original: EelApi,
  private val systemIndependentPaths: Boolean = true,
) : EelApi by EelApiWithProcessBuilderNormalization(original, { originalExecApi ->
  object : EelExecApi.ExecuteProcessBuilder by originalExecApi {
    private fun normalizeIfPath(maybePath: String): String {
      return if (maybePath.startsWith(prefix)) {
        maybePath.removePrefix(prefix).let { if (systemIndependentPaths) FileUtil.toSystemIndependentName(it) else it }
      }
      else {
        maybePath
      }
    }

    override val args: List<String> = originalExecApi.args.map(::normalizeIfPath)
    override val env: Map<String, String> = originalExecApi.env.mapValues { (_, value) -> normalizeIfPath(value) }
  }
})

@Internal
class EelApiWithProcessBuilderNormalization(
  private val original: EelApi,
  private val normalize: (EelExecApi.ExecuteProcessBuilder) -> EelExecApi.ExecuteProcessBuilder,
) : EelApi by original {
  override val exec: EelExecApi get() = EelExecApiWithNormalisation(original.exec, normalize)

  private class EelExecApiWithNormalisation(
    private val original: EelExecApi,
    private val normalize: (EelExecApi.ExecuteProcessBuilder) -> EelExecApi.ExecuteProcessBuilder,
  ) : EelExecApi by original {
    override suspend fun execute(builder: EelExecApi.ExecuteProcessBuilder): EelExecApi.ExecuteProcessResult {
      return original.execute(normalize(builder))
    }
  }
}