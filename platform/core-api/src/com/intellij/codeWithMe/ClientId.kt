// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeWithMe

import com.intellij.concurrency.IntelliJContextElement
import com.intellij.concurrency.client.*
import com.intellij.concurrency.currentThreadContext
import com.intellij.concurrency.currentThreadContextOrNull
import com.intellij.concurrency.installThreadContext
import com.intellij.diagnostic.LoadingState
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.AccessToken
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.client.ClientSessionsManager
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.IncorrectOperationException
import com.intellij.util.Processor
import com.intellij.util.ThrowableRunnable
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.function.BiConsumer
import java.util.function.Function
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

private val logger = logger<ClientId>()

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
     * Write error to logger and return localId
     */
    LOG_ERROR,
  }

  companion object {
    private val LOG = Logger.getInstance(ClientId::class.java)
    fun getClientIdLogger(): Logger = LOG

    /**
     * Default client id for local application
     */
    val defaultLocalId: ClientId = ClientId("Host")

    /**
     * Specifies behavior for [ClientId.current]
     */
    val absenceBehaviorValue: AbsenceBehavior get() {
      if (!LoadingState.COMPONENTS_LOADED.isOccurred)
        return AbsenceBehavior.RETURN_LOCAL
      if (!Registry.getInstance().isLoaded) {
        return AbsenceBehavior.RETURN_LOCAL
      }
      return absenceBehaviorValueCached
    }

    // This set used to avoid leaking frontend session client ids to global world,
    // because it can cause bugs with mixing frontend and remote sessions
    //
    // We originally planned to have multiple local sessions (one for each rider backend process),
    // but this doesn't work because many places use ClientId.Local or try to guess the local identifier (e.g. com.intellij.codeInsight.actions.ReaderModeSettingsImpl is registered as local)
    // and this prevents the rider from opening multiple projects.
    // So for now we have this hack and when Rider can open each project in a separate process (or fix all the problems with multiple local sessions)
    // we will be able to get rid of this hack and mark frontend sessions as local
    private val fakeLocalIds = ConcurrentHashMap<String, Unit>().keySet(Unit)

    @ApiStatus.Internal
    @Deprecated("This api will be removed")
    // This api will be removed as soon as Rider is able to run separate projects in different processes. Ask Rider Team
    fun isFakeLocalId(clientId: ClientId) = fakeLocalIds.contains(clientId.value)

    @ApiStatus.Internal
    @Deprecated("This api will be removed")
    // This api will be removed as soon as Rider is able to run separate projects in different processes. Ask Rider Team
    fun isFakeLocalId(clientId: String) = fakeLocalIds.contains(clientId)

    private val absenceBehaviorValueCached: AbsenceBehavior by lazy {
      val selectedOption = Registry.get("clientid.absence.behavior").selectedOption ?: return@lazy AbsenceBehavior.RETURN_LOCAL
      return@lazy try {
        AbsenceBehavior.valueOf(selectedOption)
      }
      catch (t: Throwable) {
        logger.error("Wrong option '$selectedOption' for registry key 'clientid.absence.behavior'")
        AbsenceBehavior.RETURN_LOCAL
      }
    }

    /**
     * The ID considered local to this process. All other IDs (except for null) are considered remote
     */
    @JvmStatic
    var localId: ClientId = defaultLocalId
      private set

    /**
     * The ID for the owner of RD/CWM session.
     * In case of CWM, it refers to [defaultLocalId].
     * In case of RD, it refers to the controller ID.
     *
     * **Note:** returned value makes sense only for a host machine
     */
    @JvmStatic
    var ownerId: ClientId = defaultLocalId
      private set

    /**
     * True if and only if the current [ClientId] is local to this process
     */
    @JvmStatic
    val isCurrentlyUnderLocalId: Boolean
      get() {
        val clientIdValue = getCurrentIdValidated()
        return clientIdValue == null || clientIdValue == localId.value || fakeLocalIds.contains(clientIdValue)
      }

    /**
     * Gets the current [ClientId]. Subject to [absenceBehaviorValue]
     */
    @JvmStatic
    val current: ClientId
      get() = when (absenceBehaviorValue) {
        AbsenceBehavior.RETURN_LOCAL -> currentOrNull ?: localId
        AbsenceBehavior.LOG_ERROR -> {
          val currentId = currentOrNull
          if (currentId == null) {
            logger.error("'ClientId.current' is not set'")
            localId
          }
          else {
            currentId
          }
        }
      }

    @JvmStatic
    @ApiStatus.Internal
    // optimization method for avoiding allocating ClientId in the hot path
    fun getCurrentValue(): String {
      val service = getCachedService()
      return if (service == null) localId.value else getCurrentIdValidated() ?: localId.value
    }

    /**
     * Gets the current [ClientId]. Can be null if none was set.
     */
    @JvmStatic
    val currentOrNull: ClientId?
      get() = getCurrentIdValidated()?.let(::ClientId)

    /**
     * Overrides the ID of the owner of CWM/RD session.
     */
    @JvmStatic
    @ApiStatus.Internal
    fun overrideOwnerId(newId: ClientId, parentDisposable: Disposable) {
      require(ownerId == defaultLocalId)
      ownerId = newId
      try {
        Disposer.register(parentDisposable) {
          ownerId = defaultLocalId
        }
      } catch (_: IncorrectOperationException) {
        // The parent is already disposed
        ownerId = defaultLocalId
      }
    }

    /**
     * Overrides the ID that is considered to be local to this process. Can be only invoked once.
     */
    @JvmStatic
    fun overrideLocalId(newId: ClientId) {
      require(localId == defaultLocalId)
      localId = newId
    }

    @ApiStatus.Internal
    @Deprecated("This api will be removed")
    // This api will be removed as soon as Rider is able to run separate projects in different processes. Ask Rider Team
    fun addFakeLocalId(id: ClientId, parentDisposable: Disposable) {
      fakeLocalIds.add(id.value)

      fun unregister() {
        fakeLocalIds.remove(id.value)
      }

      if (!Disposer.tryRegister(parentDisposable, ::unregister))
        unregister()
    }

    /**
     * Is true if and only if the given ID is considered to be local to this process
     */
    @JvmStatic
    val ClientId?.isLocal: Boolean
      get() = this == null || this == localId || fakeLocalIds.contains(value)

    /**
     * Computes a value under given [ClientId]
     *
     * **Note:** This method should not be called within a suspend context.
     * It is recommended to use `withContext(clientId.asContextElement())` instead.
     */
    @JvmStatic
    @RequiresBlockingContext
    inline fun <T> withClientId(clientId: ClientId?, action: () -> T): T {
      return withClientId(clientId, errorOnMismatch = true).use {
        action()
      }
    }

    @JvmStatic
    @RequiresBlockingContext
    inline fun <T> withExplicitClientId(clientId: ClientId?, action: () -> T): T {
      return withClientId(clientId, errorOnMismatch = false).use {
        action()
      }
    }

    private fun getCurrentIdValidated(): String? {
      val currentId = currentThreadClientId
      if (currentId != null) {
        val service = getCachedService()
        if (service != null && !service.isValid(currentId)) {
          logger.trace { "Invalid ClientId $currentId replaced with null at ${Throwable().fillInStackTrace()}" }
          return null
        }
      }
      return currentId?.value
    }

    /**
     * Computes a value under given [ClientId]
     *
     * **Note:** This method should not be called within a suspend context.
     * It is recommended to use `withContext(clientId.asContextElement())` instead.
     */
    @JvmStatic
    @RequiresBlockingContext
    fun withClientId(clientId: ClientId?): AccessToken {
      return withClientId(clientId, errorOnMismatch = true)
    }

    @JvmStatic
    @RequiresBlockingContext
    fun withExplicitClientId(clientId: ClientId?): AccessToken {
      return withClientId(clientId, errorOnMismatch = false)
    }

    @JvmStatic
    @RequiresBlockingContext
    fun withClientId(clientId: ClientId?, errorOnMismatch: Boolean): AccessToken {
      if (clientId == null) {
        if (absenceBehaviorValue == AbsenceBehavior.LOG_ERROR) {
          logger.error("Attempt to call withClientId with ClientId==null")
        }
        return AccessToken.EMPTY_ACCESS_TOKEN
      }
      return withClientIdImpl(clientId.value, errorOnMismatch = errorOnMismatch)
    }

    /**
     * Computes a value under given [ClientId]
     *
     * **Note:** This method should not be called within a suspend context.
     * It is recommended to use `withContext(clientId.asContextElement())` instead.
     */
    @JvmStatic
    @RequiresBlockingContext
    fun withClientId(clientIdValue: String): AccessToken {
      return withClientIdImpl(clientIdValue, errorOnMismatch = true)
    }

    @JvmStatic
    @RequiresBlockingContext
    fun withExplicitClientId(clientIdValue: String): AccessToken {
      return withClientIdImpl(clientIdValue, errorOnMismatch = false)
    }

    private fun withClientIdImpl(clientIdValue: String, errorOnMismatch: Boolean): AccessToken {
      val service = getCachedService()
      if (service == null) {
        return AccessToken.EMPTY_ACCESS_TOKEN
      }
      val oldClientId = currentThreadClientId ?: localId
      if (clientIdValue == oldClientId.value) {
        return AccessToken.EMPTY_ACCESS_TOKEN
      }

      val clientId = ClientId(clientIdValue)
      val newClientId = if (service.isValid(clientId)) {
        if (fakeLocalIds.contains(clientIdValue))
          localId
        else
          clientId
      }
      else {
        logger.trace { "Invalid ClientId $clientIdValue replaced with null at ${Throwable().fillInStackTrace()}" }
        null
      }

      val currentThreadContext = currentThreadContextOrNull()
      if (currentThreadContext == null) {
        return installThreadContext(ClientIdContextElement(newClientId))
      }

      val currentClientIdContextElement = currentThreadContext.clientIdContextElement
      val newContext = currentThreadContext + ClientIdContextElement(newClientId)
      if (errorOnMismatch) {
        if (currentClientIdContextElement != null && currentClientIdContextElement.clientId != newClientId) {
          logger.error("Trying to set $newClientId, but it's already set to ${currentClientIdContextElement}")
        }
      }
      return installThreadContext(newContext, replace = true)
    }

    private var service: Ref<ClientSessionsManager<*>?>? = null

    @ApiStatus.Internal
    fun getCachedService(): ClientSessionsManager<*>? {
      val cached = service
      if (cached != null) return cached.get()
      if (!LoadingState.CONFIGURATION_STORE_INITIALIZED.isOccurred) {
        return null
      }

      val app = ApplicationManager.getApplication()
      if (app == null || app.isDisposed) {
        return null
      }

      val instance = app.serviceOrNull<ClientSessionsManager<*>>()
      if (instance != null) {
        service = Ref.create(instance)
      }
      return instance
    }

    @TestOnly
    @ApiStatus.Internal
    fun nullizeCachedServiceInTest(test: ThrowableRunnable<Throwable>) {
      service = Ref.create(null)
      try {
        assert(getCachedService() == null)
        assert(getCurrentValue() == defaultLocalId.value)
        test.run()
      }
      finally {
        service = null
      }
    }

    @JvmStatic
    private fun assertClientIdMismatch(assertInfo: Pair<ClientIdContextElement?, Throwable>) {
      val currentClientIdElement = currentThreadContextOrNull()?.clientIdContextElement
      if (assertInfo.first != currentClientIdElement) {
        logger.error(Throwable("Captured is '${assertInfo.first}' but current is '$currentClientIdElement'", assertInfo.second))
      }
    }

    private fun captureInfoForAssertion(): Pair<ClientIdContextElement?, Throwable> {
      // TODO: drop capturing of Throwable because of performance
      val clientIdContextElement = currentThreadContextOrNull()?.clientIdContextElement
      return clientIdContextElement to Throwable("'$clientIdContextElement' captured at")
    }

    @JvmStatic
    fun <T> decorateFunction(action: () -> T): () -> T {
      val infoForAssertion = captureInfoForAssertion()
      return {
        assertClientIdMismatch(infoForAssertion)
        action()
      }
    }


    @JvmStatic
    fun decorateRunnable(runnable: Runnable): Runnable {
      val infoForAssertion = captureInfoForAssertion()
      return Runnable {
        assertClientIdMismatch(infoForAssertion)
        runnable.run()
      }
    }

    @JvmStatic
    fun <T> decorateCallable(callable: Callable<T>): Callable<T> {
      val infoForAssertion = captureInfoForAssertion()
      return Callable {
        assertClientIdMismatch(infoForAssertion)
        callable.call()
      }
    }

    @JvmStatic
    fun <T, R> decorateFunction(function: Function<T, R>): Function<T, R> {
      val infoForAssertion = captureInfoForAssertion()
      return Function {
        assertClientIdMismatch(infoForAssertion)
        function.apply(it)
      }
    }

    @JvmStatic
    fun <T, U> decorateBiConsumer(biConsumer: BiConsumer<T, U>): BiConsumer<T, U> {
      val infoForAssertion = captureInfoForAssertion()
      return BiConsumer { t, u ->
        assertClientIdMismatch(infoForAssertion)
        biConsumer.accept(t, u)
      }
    }

    @JvmStatic
    fun <T> decorateProcessor(processor: Processor<T>): Processor<T> {
      val infoForAssertion = captureInfoForAssertion()
      return Processor {
        assertClientIdMismatch(infoForAssertion)
        processor.process(it)
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

fun ClientId.asContextElement(): CoroutineContext.Element {
  if (ClientId.isFakeLocalId(this))
    return ClientIdContextElement(ClientId.localId)

  return ClientIdContextElement(this)
}

@ApiStatus.Internal
fun CoroutineContext.clientId(): ClientId? = this[ClientIdContextElement.Key]?.clientId

@ApiStatus.Internal
class ClientIdContextElement(val clientId: ClientId?) : AbstractCoroutineContextElement(Key) {
  private val creationTrace: Throwable? = if (logger.isTraceEnabled) Throwable() else null
  object Key : CoroutineContext.Key<ClientIdContextElement>

  override fun toString(): String = if (creationTrace != null) "$clientId. Created at:\r${creationTrace.stackTraceToString()}" else "$clientId"

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as ClientIdContextElement

    return clientId == other.clientId
  }

  override fun hashCode(): Int {
    return clientId?.hashCode() ?: 0
  }
}

val CoroutineContext.clientIdContextElement: ClientIdContextElement?
  @ApiStatus.Internal
  get() = this[ClientIdContextElement.Key]

val currentThreadClientId: ClientId?
  @ApiStatus.Internal
  get() = currentThreadContext().clientIdContextElement?.clientId
