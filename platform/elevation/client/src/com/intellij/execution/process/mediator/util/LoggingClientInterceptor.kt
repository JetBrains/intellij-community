// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.mediator.util

import io.grpc.*

internal object LoggingClientInterceptor : ClientInterceptor {
  override fun <ReqT, RespT> interceptCall(methodDescriptor: MethodDescriptor<ReqT, RespT>,
                                           callOptions: CallOptions, channel: Channel): ClientCall<ReqT, RespT> {
    val clientCall = channel.newCall(methodDescriptor, callOptions.withoutWaitForReady())
    return object : ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(clientCall) {
      override fun sendMessage(message: ReqT) {
        println("send[${methodDescriptor.fullMethodName}]: ${message.toString().trim()}")
        super.sendMessage(message)
      }

      override fun start(responseListener: Listener<RespT>, headers: Metadata) {
        val listener: Listener<RespT> = object : ForwardingClientCallListener.SimpleForwardingClientCallListener<RespT>(responseListener) {
          override fun onMessage(message: RespT) {
            println("recv[${methodDescriptor.fullMethodName}]: ${message.toString().trim()}")
            super.onMessage(message)
          }

          override fun onClose(status: Status, trailers: Metadata) {
            println("done[${methodDescriptor.fullMethodName}]: $status")
            super.onClose(status, trailers)
          }
        }
        super.start(listener, headers)
      }
    }
  }
}