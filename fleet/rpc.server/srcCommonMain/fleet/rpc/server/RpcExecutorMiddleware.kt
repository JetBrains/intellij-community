// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.rpc.server

import fleet.rpc.core.RpcMessage

interface RpcExecutorMiddleware {
  companion object : RpcExecutorMiddleware {
    override suspend fun execute(request: RpcMessage.CallRequest,
                                 execute: suspend (RpcMessage.CallRequest) -> RpcMessage): RpcMessage {
      return execute(request)
    }
  }

  suspend fun execute(request: RpcMessage.CallRequest,
                      execute: suspend (RpcMessage.CallRequest) -> RpcMessage): RpcMessage {
    return execute(request)
  }
}

operator fun RpcExecutorMiddleware.plus(another: RpcExecutorMiddleware): RpcExecutorMiddleware {
  val one = this
  return object : RpcExecutorMiddleware {
    override suspend fun execute(request: RpcMessage.CallRequest,
                                 execute: suspend (RpcMessage.CallRequest) -> RpcMessage): RpcMessage {
      return one.execute(request) { another.execute(it, execute) }
    }
  }
}