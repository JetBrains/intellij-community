// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.rpc.client.proxy

import fleet.reporting.shared.runtime.currentSpan
import fleet.reporting.shared.tracing.span
import fleet.reporting.shared.tracing.spannedScope
import fleet.rpc.RemoteApi
import fleet.rpc.RemoteApiDescriptor
import fleet.rpc.RemoteKind
import fleet.rpc.core.AssumptionsViolatedException
import fleet.rpc.core.RemoteObject
import fleet.util.async.catching
import fleet.util.async.use
import fleet.util.causeOfType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consume
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.yield
import kotlin.coroutines.CoroutineContext

/**
 * it's just like java.lang.reflect.InvocationHandler, but for suspend calls
 * well, except for the fact that composing suspend calls is tricky because of cancellability
 * */
interface SuspendInvocationHandler {
  // TODO can it be rewritten as some kind of monad to place it in return position? this one is rather counterintuitive
  /**
   * used to reliably return a resource that requires some form of disposal
   * from a suspend call that might be wrapped by another suspend call
   */
  class CallResult(val value: Any?, val dispose: () -> Unit) {
    fun map(f: (Any?) -> Any?): CallResult = CallResult(f(value), dispose)
  }

  /**
   * the resource passed to [publish] is guaranteed to
   * either reach user land or be disposed of with [CallResult.dispose]
   * */
  suspend fun call(remoteApiDescriptor: RemoteApiDescriptor<*>, method: String, args: List<Any?>, publish: (CallResult) -> Unit)
}

fun <T : RemoteApi<*>> suspendProxy(remoteApiDescriptor: RemoteApiDescriptor<T>,
                                    handler: SuspendInvocationHandler): T {
  return span("rpcProxy", { set("class", remoteApiDescriptor.getApiFqn()) }) {
    remoteApiDescriptor.clientStub { method, args ->
      // a channel is used for its onUndelieveredElement
      // handlers can be composed, each introducing a new suspension point
      // so actual `call` might have troubles returning a value atomically in presense of cancellation
      // by providing a box for a value we aim to guarantee that it will be disposed
      val feedback = Channel<SuspendInvocationHandler.CallResult>(capacity = 1) { result ->
        result.dispose()
      }
      feedback.consume {
        handler.call(remoteApiDescriptor, method, args.toList()) { result ->
          feedback.trySend(result).onFailure {
            result.dispose()
          }
        }
        feedback.receive().value
      }
    }
  }
}

fun SuspendInvocationHandler.outOfScope(
  callerContext: CoroutineContext,
  hotScope: CoroutineScope,
  calleeScope: CoroutineScope,
): SuspendInvocationHandler =
  let { delegate ->
    object : SuspendInvocationHandler {
      override suspend fun call(remoteApiDescriptor: RemoteApiDescriptor<*>,
                                method: String,
                                args: List<Any?>,
                                publish: (SuspendInvocationHandler.CallResult) -> Unit) {
        //TODO[jetzajac]: support RemoteObjects
        val causeSpan = currentSpan
        calleeScope.async {
          yield() // make sure the coroutine gets the workspace db
          spannedScope("outOfScope", {
            cause = causeSpan
            set("method", method)
            set("args", args.toString())
          }) {
            delegate.call(remoteApiDescriptor, method, args.map { arg ->
              when (arg) {
                is Flow<*> -> {
                  @Suppress("UNCHECKED_CAST")
                  (arg as Flow<Any>).flowOn(callerContext).produceIn(hotScope).consumeAsFlow()
                }
                else -> arg
              }
            }) {
              publish(it.map { res ->
                when (res) {
                  is RemoteObject -> {
                    val remoteObject = remoteApiDescriptor.getSignature(method).returnType as RemoteKind.RemoteObject
                    suspendProxy(remoteObject.descriptor, delegatingHandler(res).outOfScope(callerContext, hotScope, calleeScope))
                  }
                  is Flow<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    (res as Flow<Any>).produceIn(calleeScope).consumeAsFlow()
                  }
                  else -> res
                }
              })
            }
          }
        }
          .use { it.await() }
          .also {
            yield() // make sure the db is advanced in case of fast-path await
          }
      }
    }
  }

fun <A : RemoteApi<*>> delegatingHandler(target: A): SuspendInvocationHandler =
  object : SuspendInvocationHandler {
    override suspend fun call(remoteApiDescriptor: RemoteApiDescriptor<*>,
                              method: String,
                              args: List<Any?>,
                              publish: (SuspendInvocationHandler.CallResult) -> Unit) {
      spannedScope("delegating",
                   {
                     set("target", target.toString())
                     set("method", method)
                     set("args", args.toString())
                   }) {
        val r = catching { (remoteApiDescriptor as RemoteApiDescriptor<A>).call(target, method, args.toTypedArray()) }
          .getOrElse { x ->
            val assumptionsViolatedException = x.causeOfType<AssumptionsViolatedException>()
            when {
              assumptionsViolatedException != null -> throw assumptionsViolatedException
              else -> throw RuntimeException("delegate failed: $target.$method($args)", x)
            }
          }
        publish(SuspendInvocationHandler.CallResult(r) {})
      }
    }
  }

fun SuspendInvocationHandler.poisoned(poison: () -> Throwable?): SuspendInvocationHandler =
  object : SuspendInvocationHandler {
    override suspend fun call(remoteApiDescriptor: RemoteApiDescriptor<*>,
                              method: String,
                              args: List<Any?>,
                              publish: (SuspendInvocationHandler.CallResult) -> Unit) {
      when (val cause = poison()) {
        null -> this@poisoned.call(remoteApiDescriptor, method, args, publish)
        else -> throw cause
      }
    }
  }
