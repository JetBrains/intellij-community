// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeWithMe

import com.intellij.concurrency.IntelliJContextElement
import com.intellij.concurrency.client.*
import com.intellij.concurrency.currentThreadContext
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
import kotlinx.coroutines.ThreadContextElement
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
     * Controls propagation behavior. When false, decorateRunnable does nothing.
     */
    @JvmStatic
    var propagateAcrossThreads: Boolean by ::propagateClientIdAcrossThreads

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
      val service = getCachedService() ?: return action()

      val newClientIdValue = if (clientId == null || service.isValid(clientId)) {
        if (clientId != null && isFakeLocalId(clientId))
          localId.value
        else
          clientId?.value
      }
      else {
        getClientIdLogger().trace { "Invalid ClientId $clientId replaced with null at ${Throwable().fillInStackTrace()}" }
        null
      }

      val oldClientIdValue = currentClientIdString
      try {
        currentClientIdString = newClientIdValue
        return action()
      }
      finally {
        currentClientIdString = oldClientIdValue
      }
    }

    private fun getCurrentIdValidated(): String? {
      val currentValue = currentClientIdString
      if (currentValue != null) {
        val service = getCachedService()
        if (service != null && !service.isValid(ClientId(currentValue))) {
          getClientIdLogger().trace { "Invalid ClientId $currentValue replaced with null at ${Throwable().fillInStackTrace()}" }
          currentClientIdString = null
          return null
        }
      }
      return currentValue
    }

    class ClientIdAccessToken(private val oldClientIdValue: String?) : AccessToken() {
      override fun finish() {
        currentClientIdString = oldClientIdValue
      }
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
      if (clientId == null) {
        if (absenceBehaviorValue == AbsenceBehavior.LOG_ERROR) {
          LOG.error("Attempt to call withClientId with ClientId==null")
        }
        return AccessToken.EMPTY_ACCESS_TOKEN
      }
      return withClientId(clientId.value)
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
      val service = getCachedService()
      if (service == null) {
        return AccessToken.EMPTY_ACCESS_TOKEN
      }
      val oldClientIdValue = currentClientIdString ?: localId.value
      if (clientIdValue == oldClientIdValue) {
        return AccessToken.EMPTY_ACCESS_TOKEN
      }

      val newClientIdValue = if (service.isValid(ClientId(clientIdValue))) {
        if (fakeLocalIds.contains(clientIdValue))
          localId.value
        else
          clientIdValue
      }
      else {
        LOG.trace { "Invalid ClientId $clientIdValue replaced with null at ${Throwable().fillInStackTrace()}" }
        null
      }

      currentClientIdString = newClientIdValue
      return ClientIdAccessToken(oldClientIdValue)
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
    fun <T> decorateFunction(action: () -> T): () -> T {
      return captureClientId(action)
    }

    @JvmStatic
    fun decorateRunnable(runnable: Runnable): Runnable {
      return captureClientIdInRunnable(runnable)
    }

    @JvmStatic
    fun <T> decorateCallable(callable: Callable<T>): Callable<T> {
      return captureClientIdInCallable(callable)
    }

    @JvmStatic
    fun <T, R> decorateFunction(function: Function<T, R>): Function<T, R> {
      return captureClientIdInFunction(function)
    }

    @JvmStatic
    fun <T, U> decorateBiConsumer(biConsumer: BiConsumer<T, U>): BiConsumer<T, U> {
      return captureClientIdInBiConsumer(biConsumer)
    }

    @JvmStatic
    fun <T> decorateProcessor(processor: Processor<T>): Processor<T> {
      return captureClientIdInProcessor(processor)
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
    return ClientIdElement(ClientId.localId)

  return ClientIdElement(this)
}

private object ClientIdElementKey : CoroutineContext.Key<ClientIdElement>

private class ClientIdElement(private val clientId: ClientId) : ThreadContextElement<AccessToken>, IntelliJContextElement {

  override fun produceChildElement(oldContext: CoroutineContext, isStructured: Boolean): IntelliJContextElement = this

  override val key: CoroutineContext.Key<*> get() = ClientIdElementKey

  override fun toString(): String = clientId.toString()

  override fun updateThreadContext(context: CoroutineContext): AccessToken {
    return ClientId.withClientId(clientId)
  }

  override fun restoreThreadContext(context: CoroutineContext, oldState: AccessToken) {
    oldState.finish()
  }
}

private class ClientIdElement2(val clientId: ClientId) : AbstractCoroutineContextElement(Key), IntelliJContextElement {

  override fun produceChildElement(oldContext: CoroutineContext, isStructured: Boolean): IntelliJContextElement = this

  override fun toString(): String = clientId.toString()

  object Key : CoroutineContext.Key<ClientIdElement2>
}

@ApiStatus.Internal
fun ClientId.asContextElement2(): CoroutineContext.Element {
  if (ClientId.isFakeLocalId(this))
    return ClientIdElement2(ClientId.localId)

  return ClientIdElement2(this)
}

@ApiStatus.Internal
fun CoroutineContext.clientId(): ClientId? = this[ClientIdElement2.Key]?.clientId

@ApiStatus.Internal
fun currentThreadClientId(): ClientId? = currentThreadContext().clientId()
