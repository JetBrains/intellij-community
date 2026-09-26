// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.base

import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelExecApi
import com.intellij.platform.eel.EelHttpApi
import com.intellij.platform.eel.ExecuteProcessException
import com.intellij.platform.eel.provider.utils.awaitProcessResult
import com.intellij.platform.eel.provider.utils.stderrString
import com.intellij.platform.eel.spawnProcess
import kotlinx.coroutines.coroutineScope
import org.jetbrains.annotations.ApiStatus
import java.io.IOException

/**
 * [EelHttpApi] that runs the `curl` executable of the environment through [exec].
 *
 * It needs `curl` in the `PATH` of the environment. A native implementation replaces it when the agent learns HTTP.
 */
@ApiStatus.Internal
class CurlEelHttpApi(private val exec: EelExecApi) : EelHttpApi {
  override val descriptor: EelDescriptor get() = exec.descriptor

  @Throws(IOException::class)
  override suspend fun download(options: EelHttpApi.DownloadOptions): Unit = coroutineScope {
    val process = try {
      exec.spawnProcess("curl")
        .args("--fail", "--silent", "--show-error", "--location", "--output", options.target.toString(), "--", options.url)
        .scope(this)
        .eelIt()
    }
    catch (e: ExecuteProcessException) {
      throw IOException("Cannot start curl in ${descriptor.name}: ${e.message}", e)
    }
    val result = process.awaitProcessResult()
    if (result.exitCode != 0) {
      throw IOException("curl exited with code ${result.exitCode} for ${options.url}: ${result.stderrString.trim()}")
    }
  }
}
