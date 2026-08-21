// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent

interface IjentEventBusListener {
  fun started(method: String, nanoTimeStart: Long)
  fun finished(method: String, nanoTimeStart: Long, status: Int, nanoTimeFinish: Long)
}

interface IjentEventBus {
  fun addListener(listener: IjentEventBusListener)
  fun removeListener(listener: IjentEventBusListener)
}
