// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeWithMe

import com.intellij.concurrency.IntelliJContextElement
import com.intellij.concurrency.currentThreadContext
import kotlinx.coroutines.CopyableThreadContextElement
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.CoroutineContext

@OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
@DelicateCoroutinesApi
@ApiStatus.Internal
object ClientIdContextElementPrecursor : CopyableThreadContextElement<Unit>, CoroutineContext.Key<ClientIdContextElementPrecursor>, IntelliJContextElement {

  override fun copyForChild(): CopyableThreadContextElement<Unit> {
    // there is only a precursor in the scope -> replace with a ClientId element from the thread local storage
    val clientIdContextElement = currentThreadContext().clientIdContextElement
    if (clientIdContextElement != null) return clientIdContextElement
    return this
  }

  override fun mergeForChild(overwritingElement: CoroutineContext.Element): CoroutineContext {
    val existingClientIdElement = overwritingElement[ClientIdContextElement.Key]
    if (existingClientIdElement != null) return overwritingElement.minusKey(ClientIdContextElementPrecursor) // real client id is here - we remove precursor
    val clientIdContextElement = currentThreadContext().clientIdContextElement
    if (clientIdContextElement != null) return overwritingElement.minusKey(ClientIdContextElementPrecursor) + clientIdContextElement // remove precursor but add client id
    return overwritingElement // keep as is if impossible to determine ClientId
  }

  override fun updateThreadContext(context: CoroutineContext) {
  }

  override fun restoreThreadContext(context: CoroutineContext, oldState: Unit) {
  }

  override val key: CoroutineContext.Key<*>
    get() = this
}