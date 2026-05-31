// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

data class IjentCallerContext(
  val isRead: Boolean,
  val isWrite: Boolean,
  val isDispatchThread: Boolean,
) {
  companion object {
    suspend fun getSaved(): IjentCallerContext? {
      return currentCoroutineContext()[IjentCalledContextElement.Key]?.callerContext
    }
  }
}

fun IjentCallerContext.allowCancellableNio(): Boolean {
  return when {
    isRead && !isWrite -> IjentRegistry.getInstance().isEnabled("ijent.nio.cancellable.read", true)
    else -> false
  }
}

fun IjentCallerContext.unavailableDialogTimeout(): Duration {
  return if (IjentRegistry.getInstance().isEnabled("ijent.unavailable.dialog.enabled", true)) {
    if (isDispatchThread) {
      500.milliseconds
    }
    else {
      1000.milliseconds
    }
  }
  else Duration.INFINITE
}

class IjentCalledContextElement(val callerContext: IjentCallerContext) : AbstractCoroutineContextElement(Key) {
  object Key : CoroutineContext.Key<IjentCalledContextElement>
}

// TODO It is a copy-paste from Fleet, and it's better be generalized and put into some generic place.
fun CoroutineScope.coroutineNameAppended(name: String, separator: String = " > "): CoroutineContext =
  coroutineContext.coroutineNameAppended(name, separator)

fun CoroutineContext.coroutineNameAppended(name: String, separator: String = " > "): CoroutineContext {
  val parentName = this[CoroutineName]?.name
  return CoroutineName(if (parentName == null) name else parentName + separator + name)
}