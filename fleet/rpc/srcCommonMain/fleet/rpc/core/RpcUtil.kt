// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.rpc.core

import kotlinx.coroutines.suspendCancellableCoroutine

internal typealias RequestCompletionHandler = (cause: Throwable?) -> Unit

internal fun classMethodDisplayName(serviceName: String, method: String): String {
  return "$serviceName#$method"
}

fun methodParamDisplayName(methodDisplayName: String, parameterName: String): String {
  return "${methodDisplayName}($parameterName)"
}

suspend fun sendSuspend(sendAsync: (TransportMessage, RequestCompletionHandler) -> Unit, message: TransportMessage) {
  suspendCancellableCoroutine { continuation ->
    sendAsync(message) { ex: Throwable? ->
      val result = when (ex) {
        null -> Result.success(Unit)
        else -> Result.failure(ex)
      }
      continuation.resumeWith(result)
    }
  }
}
