// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.impl.wsl

import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.platform.eel.ExecuteProcessException
import com.intellij.platform.eel.getShell
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.platform.eel.provider.utils.EelProcessExecutionResult
import com.intellij.platform.eel.provider.utils.stderrString
import com.intellij.platform.eel.provider.utils.stdoutString
import com.intellij.platform.eel.spawnProcess
import com.intellij.platform.ijent.IjentPosixApi
import com.intellij.platform.ijent.spi.IjentThreadPool
import com.intellij.util.io.awaitExit
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

/**
 * Check if [desc] works in network mirrored mode.
 * If no [eelApi] provided, `toEelApi` will be used
 */
internal suspend fun isMirroredMode(desc: WslEelDescriptor, eelApi: IjentPosixApi?): Boolean = m.withLock {
  val cachedValue = mirrorModeCache.getIfPresent(desc)
  if (cachedValue != null) {
    return cachedValue
  }
  val eelApi = eelApi ?: desc.toEelApi()
  val result = run {
    val (shell, arg) = eelApi.exec.getShell()
    val result = try {
      val process = eelApi.exec.spawnProcess(shell, arg, "wslinfo --networking-mode").eelIt().convertToJavaProcess()
      process.getResultOnSeparatePool()
    }
    catch (e: ExecuteProcessException) {
      log.warn("Couldn't check networking mode", e)
      return@run false
    }
    if (result.exitCode != 0) {
      log.warn("wslinfo died with ${result.exitCode} : ${result.stderrString}")
      return@run false
    }
    return@run result.stdoutString.trim() == "mirrored"
  }
  log.info("For $desc mirrored mode is $result")
  mirrorModeCache.put(desc, result)
  return result
}

private val log = fileLogger()

private val mirrorModeCache = Caffeine.newBuilder()
  .maximumSize(10)
  .expireAfterWrite(10.minutes.toJavaDuration())
  .build<WslEelDescriptor, Boolean>()
private val m = Mutex()

/**
 * Due to contracts checked by `IjentRestartTest` and friends, one can't use [kotlinx.coroutines.Dispatchers.IO]
 * here not to introduce thread starvation and deadlock, so we can't use [com.intellij.platform.eel.EelProcess] API to copy data
 * as it depends on [kotlinx.coroutines.Dispatchers.IO] heavily.
 *
 */
private suspend fun Process.getResultOnSeparatePool(): EelProcessExecutionResult = withContext(IjentThreadPool.coroutineContext) {
  val out = async { inputStream.readAllBytes() }
  val err = async { errorStream.readAllBytes() }
  val exitCode = awaitExit()
  EelProcessExecutionResult(exitCode = exitCode, stdout = out.await(), stderr = err.await())
}
