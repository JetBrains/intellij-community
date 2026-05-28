// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent

interface IjentEventBusListener {
  fun onEvent(event: IjentEvent)
}

sealed class IjentEvent {
  abstract val method: String
}

data class IjentRequestEvent(
  override val method: String,
  val nanoTimeStart: Long
) : IjentEvent()

data class IjentResponseEvent(
  val request: IjentRequestEvent,
  val status: Int,
  val nanoTimeFinish: Long,
) : IjentEvent() {
  override val method: String
    get() = request.method
}

interface IjentEventBus {
  fun addListener(listener: IjentEventBusListener)
  fun removeListener(listener: IjentEventBusListener)
}
