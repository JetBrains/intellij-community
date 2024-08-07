// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * This service MUST know about all running IJents.
 * It also MAY know about delegates and wrappers over interfaces, if they are registered with [register].
 */
@Service(Service.Level.APP)
class IjentSessionRegistry(private val coroutineScope: CoroutineScope) {
  private val counter = AtomicLong()

  private class IjentBundle(
    val factory: suspend (ijentId: IjentId) -> IjentApi,
    val deferred: Deferred<IjentApi>?,
    val oneOff: Boolean,
  )

  private val ijents: MutableMap<IjentId, IjentBundle> = ConcurrentHashMap()

  /**
   * [ijentName] is used for debugging utilities like logs and thread names.
   *
   * When [oneOff] is true, [launcher] may be called at most once, and if something wrong happens with the returned IJent,
   * [get] still keeps returning that broken instance of [IjentApi].
   *
   * When [oneOff] is false, [get] checks if an already created [IjentApi] is functional, and if there's a problem, [get] calls
   * [launcher] again and remembers the new instance.
   *
   * [launcher] should use the provided coroutine scope for launching various jobs, passing to the implementation of [IjentApi], etc.
   */
  fun register(
    ijentName: String,
    oneOff: Boolean,
    launcher: suspend (ijentId: IjentId) -> IjentApi,
  ): IjentId {
    val ijentId = IjentId("ijent-${counter.getAndIncrement()}-${ijentName.replace(Regex("[^A-Za-z0-9-]"), "-")}")
    ijents[ijentId] = IjentBundle(launcher, null, oneOff)
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
  suspend fun get(ijentId: IjentId): IjentApi {
    val bundle = ijents.compute(ijentId, @Suppress("NAME_SHADOWING") { ijentId, oldBundle ->
      require(oldBundle != null) {
        "Not registered: $ijentId"
      }

      val oldDeferred = oldBundle.deferred

      val reusedOldDeferred: Deferred<IjentApi>? = when {
        oldDeferred == null -> null
        oldBundle.oneOff -> oldDeferred
        !oldDeferred.isCompleted -> oldDeferred
        oldDeferred.getCompletionExceptionOrNull() != null -> null
        oldDeferred.getCompleted().isRunning -> oldDeferred
        else -> null
      }

      val actualDeferred =
        reusedOldDeferred
        ?: coroutineScope.async(start = CoroutineStart.LAZY) {
          oldBundle.factory(ijentId)
        }

      IjentBundle(
        factory = oldBundle.factory,
        deferred = actualDeferred,
        oneOff = oldBundle.oneOff,
      )
    })!!
    return bundle.deferred!!.await()
  }

  companion object {
    @JvmStatic
    suspend fun instanceAsync(): IjentSessionRegistry =
      serviceAsync()

    @RequiresBlockingContext
    @JvmStatic
    fun instance(): IjentSessionRegistry =
      ApplicationManager.getApplication().service()
  }
}