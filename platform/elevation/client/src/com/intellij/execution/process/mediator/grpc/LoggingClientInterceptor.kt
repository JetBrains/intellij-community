// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.process.mediator.grpc

import com.intellij.execution.process.mediator.ProcessMediatorLogger
import io.grpc.*
import java.util.logging.Level

internal object LoggingClientInterceptor : ClientInterceptor {
  override fun <ReqT, RespT> interceptCall(methodDescriptor: MethodDescriptor<ReqT, RespT>,
                                           callOptions: CallOptions, channel: Channel): ClientCall<ReqT, RespT> {
    val clientCall = channel.newCall(methodDescriptor, callOptions.withoutWaitForReady())
    return object : ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(clientCall) {
      override fun sendMessage(message: ReqT) {
        trace("send", methodDescriptor) { message.toString().removeSuffix("\n") }
        super.sendMessage(message)
      }

      override fun start(responseListener: Listener<RespT>, headers: Metadata) {
        val listener: Listener<RespT> = object : ForwardingClientCallListener.SimpleForwardingClientCallListener<RespT>(responseListener) {
          override fun onMessage(message: RespT) {
            trace("recv", methodDescriptor) { message.toString().removeSuffix("\n") }
            super.onMessage(message)
          }

          override fun onClose(status: Status, trailers: Metadata) {
            trace("close", methodDescriptor) { status.toString() }
            super.onClose(status, trailers)
          }
        }
        super.start(listener, headers)
      }
    }
  }

  private inline fun <ReqT, RespT> trace(event: String, methodDescriptor: MethodDescriptor<ReqT, RespT>, messageSupplier: () -> String) {
    with(ProcessMediatorLogger.LOG) {
      if (isLoggable(Level.FINER)) {
        val fullName = methodDescriptor.fullMethodName
        val methodName = fullName.substringAfterLast('/')
        val qualifiedServiceName = fullName.substringBeforeLast('/', missingDelimiterValue = "")
        val simpleServiceName = qualifiedServiceName.substringAfterLast('.')
        finer("$event[$simpleServiceName/$methodName]: ${messageSupplier()}")
      }
    }
  }
}