// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.rpc.client.proxy

import fleet.reporting.shared.tracing.spannedScope
import fleet.rpc.RemoteApiDescriptor
import kotlinx.coroutines.CancellationException

interface InvocationHandlerFactory<T> {
  fun handler(arg: T): SuspendInvocationHandler
}

fun <T> InvocationHandlerFactory<T>.tracing(): InvocationHandlerFactory<T> {
  val delegate = this
  return object : InvocationHandlerFactory<T> {
    override fun handler(arg: T): SuspendInvocationHandler {
      val handler = delegate.handler(arg)
      return object : SuspendInvocationHandler {
        override suspend fun call(remoteApiDescriptor: RemoteApiDescriptor<*>,
                                  method: String,
                                  args: List<Any?>,
                                  publish: (SuspendInvocationHandler.CallResult) -> Unit) {
          spannedScope("rpc", {
            set("method", method)
            set("args", args.toString())
          }) {
            handler.call(remoteApiDescriptor, method, args, publish)
          }
        }
      }
    }
  }
}

fun <T> InvocationHandlerFactory<T>.poisoned(poison: () -> Throwable?): InvocationHandlerFactory<T> {
  val delegate = this
  return object : InvocationHandlerFactory<T> {
    override fun handler(arg: T): SuspendInvocationHandler {
      return delegate.handler(arg).poisoned(poison)
    }
  }
}
