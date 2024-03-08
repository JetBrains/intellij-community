// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/** This service should know about all running IJents. */
@ApiStatus.Experimental
@Service(Service.Level.APP)
class IjentSessionRegistry {
  val ijents: Map<IjentId, IjentApi> get() = ijentsInternal

  private val counter = AtomicLong()

  private val ijentsInternal = ConcurrentHashMap<IjentId, IjentApi>()

  /**
   * [ijentName] is used for debugging utilities like logs and thread names.
   *
   * [launcher] should use the provided coroutine scope for launching various jobs, passing to the implementation of [IjentApi], etc.
   */
  @OptIn(ExperimentalContracts::class)
  internal suspend fun register(
    ijentName: String,
    launcher: suspend (ijentId: IjentId) -> IjentApi,
  ): IjentApi {
    contract {
      callsInPlace(launcher, InvocationKind.EXACTLY_ONCE)
    }
    val ijentId = IjentId("ijent-${counter.getAndIncrement()}-${ijentName.replace(Regex("[^A-Za-z0-9-]"), "-")}")
    val ijentApi = launcher(ijentId)

    ijentsInternal[ijentId] = ijentApi
    return ijentApi
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