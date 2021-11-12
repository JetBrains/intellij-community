// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeWithMe

import com.intellij.codeWithMe.ClientId.Companion.withClientId
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.AccessToken
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.util.Disposer
import com.intellij.util.Processor
import kotlinx.coroutines.ThreadContextElement
import java.util.concurrent.Callable
import java.util.function.BiConsumer
import java.util.function.Function
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * ClientId is a global context class that is used to distinguish the originator of an action in multi-client systems
 * In such systems, each client has their own ClientId. Current process also can have its own ClientId, with this class providing methods to distinguish local actions from remote ones.
 *
 * It's up to the application to preserve and propagate the current value across background threads and asynchronous activities.
 */
data class ClientId(val value: String) {
  enum class AbsenceBehavior {
    /**
     * Return localId if ClientId is not set
     */
    RETURN_LOCAL,

    /**
     * Throw an exception if ClientId is not set
     */
    THROW
  }

  companion object {
    private val LOG = Logger.getInstance(ClientId::class.java)
    fun getClientIdLogger() = LOG

    /**
     * Default client id for local application
     */
    val defaultLocalId = ClientId("Host")

    /**
     * Specifies behavior for [ClientId.current]
     */
    var AbsenceBehaviorValue = AbsenceBehavior.RETURN_LOCAL

    /**
     * Controls propagation behavior. When false, decorateRunnable does nothing.
     */
    @JvmStatic
    var propagateAcrossThreads = false

    /**
     * The ID considered local to this process. All other IDs (except for null) are considered remote
     */
    @JvmStatic
    var localId = defaultLocalId
      private set

    /**
     * True if and only if the current [ClientId] is local to this process
     */
    @JvmStatic
    val isCurrentlyUnderLocalId: Boolean
      get() = currentOrNull.isLocal

    /**
     * Gets the current [ClientId]. Subject to [AbsenceBehaviorValue]
     */
    @JvmStatic
    val current: ClientId
      get() = when (AbsenceBehaviorValue) {
        AbsenceBehavior.RETURN_LOCAL -> currentOrNull ?: localId
        AbsenceBehavior.THROW -> currentOrNull ?: throw NullPointerException("ClientId not set")
      }

    /**
     * Gets the current [ClientId]. Can be null if none was set.
     */
    @JvmStatic
    val currentOrNull: ClientId?
      get() = ClientIdService.tryGetInstance()?.clientIdValue?.let(::ClientId)

    /**
     * Overrides the ID that is considered to be local to this process. Can be only invoked once.
     */
    @JvmStatic
    fun overrideLocalId(newId: ClientId) {
      require(localId == defaultLocalId)
      localId = newId
    }

    /**
     * Returns true if and only if the given ID is considered to be local to this process
     */
    @JvmStatic
    @Deprecated("Use ClientId.isLocal", ReplaceWith("clientId.isLocal", "com.intellij.codeWithMe.ClientId.Companion.isLocal"))
    fun isLocalId(clientId: ClientId?): Boolean {
      return clientId.isLocal
    }

    /**
     * Is true if and only if the given ID is considered to be local to this process
     */
    @JvmStatic
    val ClientId?.isLocal: Boolean
      get() = this == null || this == localId

    /**
     * Returns true if the given ID is local or a client is still in the session.
     * Consider subscribing to a proper lifetime instead of this check.
     */
    @JvmStatic
    @Deprecated("Use ClientId.isValid", ReplaceWith("clientId.isValid", "com.intellij.codeWithMe.ClientId.Companion.isValid"))
    fun isValidId(clientId: ClientId?): Boolean {
      return clientId.isValid
    }

    /**
     * Is true if the given ID is local or a client is still in the session.
     * Consider subscribing to a proper lifetime instead of this check
     */
    @JvmStatic
    val ClientId?.isValid: Boolean
      get() = ClientIdService.tryGetInstance()?.isValid(this) ?: true

    /**
     * Returns a disposable object associated with the given ID.
     * Consider using a lifetime that is usually passed along with the ID
     */
    @JvmStatic
    fun ClientId?.toDisposable(): Disposable {
      return ClientIdService.tryGetInstance()?.toDisposable(this) ?: Disposer.newDisposable()
    }

    /**
     * Invokes a runnable under the given [ClientId]
     */
    @JvmStatic
    @Suppress("DeprecatedCallableAddReplaceWith")
    @Deprecated("Consider using an overload that returns a AccessToken to follow java try-with-resources pattern")
    fun withClientId(clientId: ClientId?, action: Runnable) = withClientId(clientId) { action.run() }

    /**
     * Computes a value under given [ClientId]
     */
    @JvmStatic
    @Suppress("DeprecatedCallableAddReplaceWith")
    @Deprecated("Consider using an overload that returns an AccessToken to follow java try-with-resources pattern")
    fun <T> withClientId(clientId: ClientId?, action: Callable<T>): T = withClientId(clientId) { action.call() }

    /**
     * Computes a value under given [ClientId]
     */
    @JvmStatic
    inline fun <T> withClientId(clientId: ClientId?, action: () -> T): T {
      val service = ClientIdService.tryGetInstance() ?: return action()

      val newClientIdValue = if (!service.isValid(clientId)) {
        getClientIdLogger().trace { "Invalid ClientId $clientId replaced with null at ${Throwable().fillInStackTrace()}" }
        null
      }
      else {
        clientId?.value
      }

      val oldClientIdValue = service.clientIdValue
      try {
        service.clientIdValue = newClientIdValue
        return action()
      }
      finally {
        service.clientIdValue = oldClientIdValue
      }
    }

    @JvmStatic
    fun withClientId(clientId: ClientId?) = object : AccessToken() {
      private val service = ClientIdService.tryGetInstance()
      private var oldClientIdValue: String? = null

      init {
        if (service != null) {
          val newClientIdValue = if (!service.isValid(clientId)) {
            LOG.trace { "Invalid ClientId $clientId replaced with null at ${Throwable().fillInStackTrace()}" }
            null
          }
          else clientId?.value

          oldClientIdValue = service.clientIdValue
          service.clientIdValue = newClientIdValue
        }
      }

      override fun finish() {
        service?.clientIdValue = oldClientIdValue
      }
    }

    @JvmStatic
    fun <T> decorateFunction(action: () -> T): () -> T {
      if (propagateAcrossThreads) return action
      val currentId = currentOrNull
      return {
        withClientId(currentId) {
          return@withClientId action()
        }
      }
    }

    @JvmStatic
    fun decorateRunnable(runnable: Runnable): Runnable {
      if (!propagateAcrossThreads) {
        return runnable
      }

      val currentId = currentOrNull
      return Runnable {
        withClientId(currentId) { runnable.run() }
      }
    }

    @JvmStatic
    fun <T> decorateCallable(callable: Callable<T>): Callable<T> {
      if (!propagateAcrossThreads) return callable
      val currentId = currentOrNull
      return Callable { withClientId(currentId, callable) }
    }

    @JvmStatic
    fun <T, R> decorateFunction(function: Function<T, R>): Function<T, R> {
      if (!propagateAcrossThreads) return function
      val currentId = currentOrNull
      return Function { withClientId(currentId) { function.apply(it) } }
    }

    @JvmStatic
    fun <T, U> decorateBiConsumer(biConsumer: BiConsumer<T, U>): BiConsumer<T, U> {
      if (!propagateAcrossThreads) return biConsumer
      val currentId = currentOrNull
      return BiConsumer { t, u -> withClientId(currentId) { biConsumer.accept(t, u) } }
    }

    @JvmStatic
    fun <T> decorateProcessor(processor: Processor<T>): Processor<T> {
      if (!propagateAcrossThreads) return processor
      val currentId = currentOrNull
      return Processor { withClientId(currentId) { processor.process(it) } }
    }

    /** Sets current [ClientId].
     * Please, TRY NOT TO USE THIS METHOD except cases you sure you know what it does and there is no other ways.
     * In most cases it's convenient and preferable to use [withClientId].
     */
    @JvmStatic
    fun trySetCurrentClientId(clientId: ClientId?) {
      val clientIdService = ClientIdService.tryGetInstance()
      if (clientIdService != null) {
        clientIdService.clientIdValue = clientId?.value
      }
    }

    fun coroutineContext(): CoroutineContext = currentOrNull?.asContextElement() ?: EmptyCoroutineContext
  }
}

fun isForeignClientOnServer(): Boolean {
  return !ClientId.isCurrentlyUnderLocalId && ClientId.localId == ClientId.defaultLocalId
}

fun isOnGuest(): Boolean {
  return ClientId.localId != ClientId.defaultLocalId
}

fun ClientId.asContextElement(): CoroutineContext.Element = ClientIdElement(this)

private object ClientIdElementKey : CoroutineContext.Key<ClientIdElement>

private class ClientIdElement(private val clientId: ClientId) : ThreadContextElement<AccessToken> {

  override val key: CoroutineContext.Key<*> get() = ClientIdElementKey

  override fun updateThreadContext(context: CoroutineContext): AccessToken {
    return ClientId.withClientId(clientId)
  }

  override fun restoreThreadContext(context: CoroutineContext, oldState: AccessToken): Unit {
    oldState.finish()
  }
}
