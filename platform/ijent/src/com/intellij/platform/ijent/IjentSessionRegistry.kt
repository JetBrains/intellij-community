// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent

import com.intellij.platform.ijent.IjentSessionRegistry.register
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * This service MUST know about a running IJent if it's going to be used through [java.nio.file.spi.FileSystemProvider].
 * It also MAY know about delegates and wrappers over interfaces, if they are registered with [register].
 */
@OptIn(DelicateCoroutinesApi::class)
object IjentSessionRegistry {
  private val counter = AtomicLong()

  private class IjentBundle(
    val factory: suspend (ijentId: IjentId) -> IjentSession.Posix,
    val deferred: Deferred<IjentSession.Posix>?,
    val oneOff: Boolean,
  )

  private val ijents: MutableMap<IjentId, IjentBundle> = ConcurrentHashMap()

  /**
   * [ijentName] is used for debugging utilities like logs and thread names.
   *
   * If something happens with IJent, and it starts throwing [IjentUnavailableException],
   * [launch] is called again to make a new instance of [IjentApi].
   */
  fun register(
    ijentName: String,
    launcher: suspend (ijentId: IjentId) -> IjentSession.Posix,
  ): IjentId {
    val ijentId = IjentId("ijent-${counter.getAndIncrement()}-${ijentName.replace(Regex("[^A-Za-z0-9-]"), "-")}")
    ijents[ijentId] = IjentBundle(launcher, null, oneOff = false)
    return ijentId
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  fun unregister(ijentId: IjentId): Boolean {
    val ijentDeferred = ijents.remove(ijentId)?.deferred
    if (ijentDeferred != null) {
      ijentDeferred.invokeOnCompletion {
        if (it == null) {
          ijentDeferred.getCompleted().close()
        }
      }

      // If the scope is already canceled, this cancellation won't have any effect.
      // So, this message with "during initialization" has an effect only if the IJent is really being initialized.
      val message = "Explicitly unregistered and closed during initialization: $ijentId"
      ijentDeferred.cancel(message, IjentUnavailableException.ClosedByApplication(message))
    }
    return ijentDeferred != null
  }

  /**
   * This method can throw [IjentUnavailableException] if it's impossible to create an IJent process.
   * Also, the returned [IjentApi] still may break down during its usage and throw [IjentUnavailableException] from any method.
   *
   * An instance of [IjentApi] that has ever thrown [IjentUnavailableException] will never be returned by this function again.
   */
  @OptIn(ExperimentalCoroutinesApi::class)
  suspend fun get(ijentId: IjentId): IjentSession.Posix {
    val currentDispatcher = currentCoroutineDispatcher()
    val bundle = ijents.compute(ijentId, @Suppress("NAME_SHADOWING") { ijentId, oldBundle ->
      require(oldBundle != null) {
        "Not registered: $ijentId"
      }

      val oldDeferred = oldBundle.deferred

      val reusedOldDeferred: Deferred<IjentSession.Posix>? = when {
        oldDeferred == null -> null
        oldBundle.oneOff -> oldDeferred
        !oldDeferred.isCompleted -> oldDeferred
        oldDeferred.getCompletionExceptionOrNull() != null -> null
        oldDeferred.getCompleted().isRunning -> oldDeferred
        else -> null
      }

      val actualDeferred =
        reusedOldDeferred
        ?: GlobalScope.async(context = currentDispatcher, start = CoroutineStart.LAZY) {
          oldBundle.factory(ijentId)
        }

      IjentBundle(
        factory = oldBundle.factory,
        deferred = actualDeferred,
        oneOff = oldBundle.oneOff,
      )
    })!!
    try {
      return bundle.deferred!!.await()
    }
    catch (err: Throwable) {
      throw IjentUnavailableException.unwrapFromCancellationExceptions(err)
    }
  }
}