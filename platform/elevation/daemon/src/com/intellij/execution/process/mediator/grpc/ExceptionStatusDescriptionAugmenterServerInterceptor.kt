// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.mediator.grpc

import io.grpc.*

internal object ExceptionStatusDescriptionAugmenterServerInterceptor : ServerInterceptor {
  override fun <ReqT : Any?, RespT : Any?> interceptCall(call: ServerCall<ReqT, RespT>,
                                                         headers: Metadata,
                                                         next: ServerCallHandler<ReqT, RespT>): ServerCall.Listener<ReqT> {
    return next.startCall(object : ForwardingServerCall.SimpleForwardingServerCall<ReqT, RespT>(call) {
      override fun close(status: Status, trailers: Metadata) {
        val newStatus = status.cause?.takeIf { status.code == Status.Code.UNKNOWN }?.let { cause ->
          status.augmentDescription(cause.message)
        } ?: status
        super.close(newStatus, trailers)
      }
    }, headers)
  }
}
