// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent

import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.platform.util.coroutines.attachAsChildTo
import com.intellij.platform.util.coroutines.namedChildScope
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.OverrideOnly
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit

/**
 * Given that there is some IJent process launched, this extension gets handles to stdin+stdout of the process and returns
 * an [IjentApi] instance for calling procedures on IJent side.
 */
@ApiStatus.Experimental
interface IjentSessionProvider {
  @get:OverrideOnly
  val epCoroutineScope: CoroutineScope

  /**
   * When calling the method, there's no need to wire [communicationCoroutineScope] to [epCoroutineScope],
   * since it is already performed by factory methods.
   *
   * Automatically registers the result in [IjentSessionRegistry].
   */
  @OverrideOnly
  suspend fun connect(
    id: IjentId,
    communicationCoroutineScope: CoroutineScope,
    platform: IjentExecFileProvider.SupportedPlatform,
    inputStream: InputStream,
    outputStream: OutputStream,
  ): IjentApi

  companion object {
    private val LOG = logger<IjentSessionProvider>()

    /**
     * The session exits when one of the following happens:
     * * The job corresponding to [communicationCoroutineScope] is finished.
     * * [epCoroutineScope] is finished.
     * * [inputStream] is closed.
     */
    @OptIn(DelicateCoroutinesApi::class)
    suspend fun connect(
      communicationCoroutineScope: CoroutineScope,
      platform: IjentExecFileProvider.SupportedPlatform,
      process: Process,
    ): IjentApi {
      val provider = serviceAsync<IjentSessionProvider>()
      val ijentsRegistry = IjentSessionRegistry.instanceAsync()
      val ijentId = ijentsRegistry.makeNewId()
      val epCoroutineScope = provider.epCoroutineScope
      val childScope = communicationCoroutineScope
        .namedChildScope(ijentId.toString(), supervisor = false)
        .apply { attachAsChildTo(epCoroutineScope) }

      childScope.coroutineContext.job.invokeOnCompletion {
        ijentsRegistry.ijentsInternal.remove(ijentId)
      }

      childScope.launch(Dispatchers.IO + childScope.coroutineNameAppended("$ijentId > watchdog")) {
        while (true) {
          if (process.waitFor(10, TimeUnit.MILLISECONDS)) {
            val exitValue = process.exitValue()
            LOG.debug { "$ijentId exit code $exitValue" }
            check(exitValue == 0) { "Process has exited with code $exitValue" }
            cancel()
            break
          }
          delay(100)
        }
      }
      childScope.launch(Dispatchers.IO + childScope.coroutineNameAppended("$ijentId > finalizer")) {
        try {
          awaitCancellation()
        }
        catch (err: Exception) {
          LOG.debug(err) { "$ijentId is going to be terminated due to receiving an error" }
          throw err
        }
        finally {
          if (process.isAlive) {
            GlobalScope.launch(Dispatchers.IO + coroutineNameAppended("actual destruction")) {
              try {
                if (process.waitFor(5, TimeUnit.SECONDS)) {
                  LOG.debug { "$ijentId exit code ${process.exitValue()}" }
                }
              }
              finally {
                if (process.isAlive) {
                  LOG.debug { "The process $ijentId is still alive, it will be killed" }
                  process.destroy()
                }
              }
            }
            GlobalScope.launch(Dispatchers.IO) {
              LOG.debug { "Closing stdin of $ijentId" }
              process.outputStream.close()
            }
          }
        }
      }

      val processScopeNamePrefix = childScope.coroutineContext[CoroutineName]?.let { "$it >" } ?: ""

      epCoroutineScope.launch(Dispatchers.IO) {
        withContext(coroutineNameAppended("$processScopeNamePrefix $ijentId > logger")) {
          process.errorReader().use { errorReader ->
            for (line in errorReader.lineSequence()) {
              // TODO It works incorrectly with multiline log messages.
              when (line.splitToSequence(Regex(" +")).drop(1).take(1).firstOrNull()) {
                "TRACE" -> LOG.trace { "$ijentId log: $line" }
                "DEBUG" -> LOG.debug { "$ijentId log: $line" }
                "INFO" -> LOG.info("$ijentId log: $line")
                "WARN" -> LOG.warn("$ijentId log: $line")
                "ERROR" -> LOG.error("$ijentId log: $line")
                else -> LOG.trace { "$ijentId log: $line" }
              }
              yield()
            }
          }
        }
      }

      val result = provider.connect(ijentId, childScope, platform, process.inputStream, process.outputStream)
      ijentsRegistry.ijentsInternal[ijentId] = result
      return result
    }
  }
}

internal class DefaultIjentSessionProvider(override val epCoroutineScope: CoroutineScope) : IjentSessionProvider {
  override suspend fun connect(
    id: IjentId,
    communicationCoroutineScope: CoroutineScope,
    platform: IjentExecFileProvider.SupportedPlatform,
    inputStream: InputStream,
    outputStream: OutputStream,
  ): IjentApi {
    throw UnsupportedOperationException()
  }
}