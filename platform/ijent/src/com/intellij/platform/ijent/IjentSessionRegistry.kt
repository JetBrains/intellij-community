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

@ApiStatus.Experimental
@Service(Service.Level.APP)
class IjentSessionRegistry {
  val ijents: Map<IjentId, IjentApi> get() = ijentsInternal

  private val counter = AtomicLong()

  internal val ijentsInternal = ConcurrentHashMap<IjentId, IjentApi>()

  internal fun makeNewId(): IjentId =
    IjentId("ijent-${counter.getAndIncrement()}")

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