// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeWithMe

import com.intellij.concurrency.IntelliJContextElement
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.CopyableThreadContextElement
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.CoroutineContext

private val logger = logger<ClientIdContextElement>()

@OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
@ApiStatus.Internal
class ClientIdContextElement(val clientId: ClientId?) : CopyableThreadContextElement<Unit>, IntelliJContextElement {
  val creationTrace: Throwable? = if (isStacktraceLoggingEnabled()) Throwable("${formatClientId()} created at") else null

  companion object Key : CoroutineContext.Key<ClientIdContextElement>

  override fun produceChildElement(parentContext: CoroutineContext, isStructured: Boolean): IntelliJContextElement = this

  override fun beforeChildStarted(context: CoroutineContext) {
    val threadClientIdElement = context.clientIdContextElement
    if (threadClientIdElement != this) {
      logger.error(Throwable("Thread context has $threadClientIdElement but coroutine context has $this").apply {
        creationTrace?.let { addSuppressed(it) }
        threadClientIdElement?.creationTrace?.let { addSuppressed(it) }
        if (suppressed.isEmpty()) addSuppressed(tracingHint())
      })
    }
  }

  override val key: CoroutineContext.Key<*>
    get() = Key

  override fun toString(): String = formatClientId()

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as ClientIdContextElement

    return clientId == other.clientId
  }

  override fun hashCode(): Int {
    return clientId?.hashCode() ?: 0
  }

  override fun updateThreadContext(context: CoroutineContext) {
  }

  override fun copyForChild(): CopyableThreadContextElement<Unit> {
    // if copyForChild is called on ClientIdContextElement (not on a precursor) it means that nothing should be done, a ClientId is here already
    return this
  }

  override fun mergeForChild(overwritingElement: CoroutineContext.Element): CoroutineContext {
    // the same for mergeForChild
    return overwritingElement
  }

  override fun restoreThreadContext(context: CoroutineContext, oldState: Unit) {
  }

  private fun formatClientId(): String = "${clientId ?: "ClientId=<null>"}"
}

private fun isStacktraceLoggingEnabled() = logger.isTraceEnabled
// better to unify with the similar in threadContext.kt later
private fun tracingHint() = Throwable("To enable stack trace recording set log category '#com.intellij.codeWithMe' to 'trace'")