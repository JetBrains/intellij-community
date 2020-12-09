// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.mediator.grpc

import com.intellij.execution.process.mediator.daemon.DaemonClientCredentials
import io.grpc.*

internal class CredentialsAuthServerInterceptor(private val credentials: DaemonClientCredentials) : ServerInterceptor {
  init {
    require(credentials != DaemonClientCredentials.EMPTY) { "Empty credentials" }
  }

  override fun <ReqT, RespT> interceptCall(call: ServerCall<ReqT, RespT>,
                                           headers: Metadata,
                                           next: ServerCallHandler<ReqT, RespT>): ServerCall.Listener<ReqT> {
    if (DaemonClientCredentials.fromMetadata(headers) != credentials) {
      call.close(Status.UNAUTHENTICATED.withDescription("Invalid token"), headers)
      return nopListener()
    }
    return next.startCall(call, headers)
  }

  companion object {
    private val NOP_LISTENER = object : ServerCall.Listener<Any>() {}

    private fun <ReqT> nopListener(): ServerCall.Listener<ReqT> {
      @Suppress("UNCHECKED_CAST")
      return NOP_LISTENER as ServerCall.Listener<ReqT>
    }
  }
}