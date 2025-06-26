// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.rpc

import com.intellij.openapi.diagnostic.Logger
import fleet.rpc.client.RpcTimeoutException
import org.jetbrains.annotations.ApiStatus

/**
 * Executes the given RPC call with retries on `RpcTimeoutException`. Logs an error message for each retry attempt.
 *
 * @param rpcCall A suspending function representing the RPC call to be executed.
 * @return The result of the RPC call upon successful execution.
 */
@ApiStatus.Internal
suspend fun <T> Logger.performRpcWithRetries(rpcCall: suspend () -> T): T {
  var attempt = 0
  while (true) {
    try {
      return rpcCall.invoke()
    }
    catch (e: RpcTimeoutException) {
      attempt++
      this.error("RPC call timed out. (attempt $attempt)", e)
    }
  }
}