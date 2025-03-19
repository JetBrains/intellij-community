// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.rpc.core.util

import fleet.util.async.catching
import fleet.util.channels.channels
import fleet.util.channels.use
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*

@OptIn(DelicateCoroutinesApi::class)
fun <T, U> ReceiveChannel<T>.map(f: (T) -> U): ReceiveChannel<U> {
  val original = this
  val (send, receive) = channels<U>()
  GlobalScope.launch(Dispatchers.Unconfined) {
    catching {
      send.use {
        original.consumeEach { send.send(f(it)) }
      }
    }
  }
  return receive
}

@OptIn(DelicateCoroutinesApi::class)
fun <T, U> SendChannel<T>.map(f: (U) -> T): SendChannel<U> {
  val original = this
  val (send, receive) = channels<U>()
  GlobalScope.launch(Dispatchers.Unconfined) {
    catching {
      original.use {
        receive.consumeEach { original.send(f(it)) }
      }
    }
  }
  return send
}
