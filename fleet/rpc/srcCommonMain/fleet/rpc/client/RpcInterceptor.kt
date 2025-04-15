// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.rpc.client

import fleet.rpc.core.RpcMessage

interface RpcInterceptor {
  companion object: RpcInterceptor {
    override suspend fun interceptCallRequest(request: RpcMessage.CallRequest): RpcMessage.CallRequest {
      return request
    }

    override suspend fun interceptCallResult(displayName: String, result: RpcMessage.CallResult) {
    }
  }

  suspend fun interceptCallRequest(request: RpcMessage.CallRequest): RpcMessage.CallRequest = request

  suspend fun interceptCallResult(displayName: String, result: RpcMessage.CallResult) {}
}

operator fun RpcInterceptor.plus(another: RpcInterceptor): RpcInterceptor {
  val one = this
  return object : RpcInterceptor {
    override suspend fun interceptCallRequest(request: RpcMessage.CallRequest): RpcMessage.CallRequest {
      return one.interceptCallRequest(another.interceptCallRequest(request))
    }

    override suspend fun interceptCallResult(displayName: String, result: RpcMessage.CallResult) {
      one.interceptCallResult(displayName, result)
      another.interceptCallResult(displayName, result)
    }
  }
}