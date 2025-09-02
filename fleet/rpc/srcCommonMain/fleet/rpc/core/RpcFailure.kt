// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.rpc.core

import kotlinx.coroutines.CopyableThrowable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.serialization.Serializable

@Serializable
data class FailureInfo(
  val authenticationError: String? = null,
  val securityError: String? = null,
  val requestError: String? = null,
  val transportError: String? = null,

  val producerCancelled: String? = null,
  val conflict: String? = null,
  val unresolvedService: String? = null,
  val serviceNotReady: String? = null,
)

fun Throwable.toFailureInfo(): FailureInfo {
  return when (this) {
    is AssumptionsViolatedException -> FailureInfo(conflict = stackTraceToString())

    // TODO : All kinds of exception: Auth, Security, Validation, Transport
    else -> FailureInfo(requestError = stackTraceToString())
  }
}

fun FailureInfo.message(): String {
  val s = when {
    this.authenticationError != null -> this.authenticationError
    this.securityError != null -> this.securityError
    this.requestError != null -> this.requestError
    this.transportError != null -> this.transportError

    this.conflict != null -> this.conflict
    this.unresolvedService != null -> this.unresolvedService
    this.serviceNotReady != null -> this.serviceNotReady
    this.producerCancelled != null -> this.producerCancelled
    else -> "unknown"
  }
  return "Failure[$s]"
}

internal fun rpcCallFailureMessage(request: RpcMessage.CallRequest, message: String): String {
  return "Remote call <${request.classMethodDisplayName()}> has failed: $message"
}

internal fun rpcStreamFailureMessage(displayName: String, message: String): String {
  return "Remote channel <${displayName}> was closed with error: $message"
}

@OptIn(ExperimentalCoroutinesApi::class)
class RpcException(message: String,
                   val failure: FailureInfo,
                   cause: Throwable?) : RuntimeException(message, cause),
                                        CopyableThrowable<RpcException> {

  // https://github.com/Kotlin/kotlinx.coroutines/blob/master/docs/topics/debugging.md#stacktrace-recovery-machinery

  constructor(message: String, failure: FailureInfo) : this("$message: ${failure.message()}", failure, null)

  companion object {
    fun callFailed(request: RpcMessage.CallRequest, failure: FailureInfo): RpcException {
      return RpcException(rpcCallFailureMessage(request, failure.message()), failure, null)
    }

    fun streamFailed(displayName: String, failure: FailureInfo): RpcException {
      return RpcException(rpcStreamFailureMessage(displayName, failure.message()), failure, null)
    }
  }

  override val message: String get() = super.message!!

  // see kotlinx.coroutines.internal.StackTraceRecovery.kt#recoverFromStackFrame
  // it it _very_ important for this copy to have exactly the same message
  override fun createCopy(): RpcException = RpcException(message, failure, this)
}
