// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util

import kotlin.coroutines.*

@RestrictsSuspension
interface LoopScope<T> {
  suspend fun br(result: T): Nothing
}

fun <T> loop(body: suspend LoopScope<T>.() -> Unit): T {
  var res: T? = null
  suspend {
    val scope = object : LoopScope<T> {
      override suspend fun br(result: T): Nothing {
        res = result
        suspendCoroutine<T> { }
        error("unreachable")
      }
    }

    while (true) {
      body(scope)
    }
  }.startCoroutine(Continuation(EmptyCoroutineContext) {})
  return res as T
}

internal fun main() {
  var i = 0
  val x = loop {
    i++
    if (i == 10) {
      br(15)
    }
  }
  println(x)
}