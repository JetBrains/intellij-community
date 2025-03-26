// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util.async

import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

fun CoroutineScope.coroutineNameAppended(name: String, separator: String = " > "): CoroutineName =
  coroutineContext.coroutineNameAppended(name, separator)

fun CoroutineContext.coroutineNameAppended(name: String, separator: String = " > "): CoroutineName {
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
