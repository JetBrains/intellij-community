// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.rpc

import com.intellij.openapi.diagnostic.Logger
import fleet.rpc.client.RpcTimeoutException

private val log = Logger.getInstance(RpcCallUtil::class.java)
object RpcCallUtil {

  suspend fun <T> invokeSafely(rpcCall: suspend () -> T): T {
    var attempt = 0
    while (true) {
      try {
        return rpcCall.invoke()
      }
      catch (_: RpcTimeoutException) {
        attempt++
        log.error("RPC call timed out. (attempt $attempt)")
      }
    }
  }
}