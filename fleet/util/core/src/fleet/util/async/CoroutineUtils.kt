// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util.async

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.StateFlow
//import kotlinx.coroutines.debug.DebugProbes
import kotlin.coroutines.CoroutineContext

private fun CoroutineScope.ensureSupervisorsReportToLogs(): CoroutineScope {
  require(coroutineContext[CoroutineExceptionHandler] != null) {
    "Creating supervisorScopes requires CoroutineExceptionHandler not to lose reported exceptions"
  }
  return this
}

fun CoroutineScope.coroutineNameAppended(name: String, separator: String = " > "): CoroutineContext =
  coroutineContext.coroutineNameAppended(name, separator)

fun CoroutineContext.coroutineNameAppended(name: String, separator: String = " > "): CoroutineContext {
  val parentName = this[CoroutineName]?.name
  return CoroutineName(if (parentName == null) name else parentName + separator + name)
}

private class RogueCancellationException(msg: String, cause: CancellationException) : Exception(msg, cause)

private const val CatchRogueCancellation: Boolean = false

suspend fun <T> catching(body: suspend CoroutineScope.() -> T): Result<T> =
  try {
    Result.success(coroutineScope { body() })
  }
  catch (c: CancellationException) {
    if (CatchRogueCancellation && currentCoroutineContext().job.isActive) {
      Result.failure(RogueCancellationException("deferred was cancelled but needed", c))
    }
    else {
      throw c
    }
  }
  catch (x: Throwable) {
    Result.failure(x)
  }

private suspend fun <T> Deferred<T>.awaitRobust(): T = awaitResult().getOrThrow()

suspend fun <T> Deferred<T>.awaitResult(): Result<T> = catching { await() }

private suspend fun <T> ReceiveChannel<T>.receiveRobust(): T = receiveResult().getOrThrow()

suspend fun <T> ReceiveChannel<T>.receiveResult(): Result<T> = catching { receive() }

fun <T, U> StateFlow<T>.view(f: (T) -> U): StateFlow<U> {
  val self = this
  return object : StateFlow<U> {
    override val replayCache: List<U>
      get() = self.replayCache.map(f)
    override val value: U
      get() = f(self.value)

    override suspend fun collect(collector: FlowCollector<U>): Nothing {
      self.collect { t ->
        collector.emit(f(t))
      }
    }
  }
}

/*
@OptIn(ExperimentalCoroutinesApi::class)
fun DebugProbes.dumpCoroutinesDeduplicated() {
  val dump = dumpCoroutinesInfo().groupBy { it.lastObservedStackTrace() }
    .flatMap { (stack, sameStack) -> sameStack.groupBy { it.state }.map { (state, coroutines) -> Triple(stack, state, coroutines.size) } }
    .map { (stack, state, count) ->
      val coroutines = if (count > 1) { "coroutines" } else { "coroutine" }
      "$count $coroutines $state \n ${stack.map { "\t at $it" }.joinToString("\n")}"
    }.joinToString("\n\n")
  println(dump)
}
*/
