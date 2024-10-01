// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeWithMe

import com.intellij.codeWithMe.ClientId.Companion.absenceBehaviorValue
import com.intellij.codeWithMe.ClientId.Companion.defaultLocalId
import com.intellij.codeWithMe.ClientId.Companion.withClientId
import com.intellij.concurrency.currentThreadContext
import com.intellij.concurrency.currentThreadContextOrNull
import com.intellij.concurrency.installThreadContext
import com.intellij.diagnostic.LoadingState
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.AccessToken
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.client.ClientSessionsManager
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.IncorrectOperationException
import com.intellij.util.Processor
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.function.BiConsumer
import java.util.function.Function
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

    /**
     * Default client id for local application
     */
    @Internal
    val defaultLocalId: ClientId = ClientId("Host")

    /**
     * Specifies behavior for [ClientId.current]
     */
    val absenceBehaviorValue: AbsenceBehavior
      @Internal
      get() {
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

    @Internal
    @Deprecated("This api will be removed")
    // This api will be removed as soon as Rider is able to run separate projects in different processes. Ask Rider Team
    fun isFakeLocalId(clientId: ClientId) = fakeLocalIds.contains(clientId.value)

    @Internal
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
        val clientIdValue = currentThreadClientId?.value
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
    @Internal
    // optimization method for avoiding allocating ClientId in the hot path
    fun getCurrentValue(): String = currentThreadClientId?.value ?: localId.value

    /**
     * Gets the current [ClientId]. Can be null if none was set.
     */
    @JvmStatic
    val currentOrNull: ClientId?
      get() = currentThreadClientId?.value?.let(::ClientId)

    /**
     * Overrides the ID of the owner of CWM/RD session.
     */
    @JvmStatic
    @Internal
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
    @Internal
    @JvmStatic
    fun overrideLocalId(newId: ClientId) {
      require(localId == defaultLocalId)
      localId = newId
    }

    @Internal
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
     *
     * **Note 2** Don't do this method inline!
     * If it's inline it's possible to do suspend calls inside [action], and thus it makes [withClientId] itself a suspend method.
     *
     * It may lead to unexpected behavior:
     * the method enters and sets [clientId] to the thread-local storage of a particular dispatcher's thread.
     * Then suspension is happening for some time, and during this suspension some other events can be processed on the same dispatcher's thread.
     * Since the thread local wasn't reset (due to the fact that the [withClientId] call is not finished yet) some event can observe this hanging [clientId],
     * which in essence should not be set for this event.
     */
    @Internal
    @JvmStatic
    fun <T> withClientId(clientId: ClientId?, action: () -> T): T {
      return withClientId(clientId, errorOnMismatch = true).use {
        action()
      }
    }

    /**
     * The same as [withClientId] but it doesn't fire a log.error()
     * when there is a ClientId on the current thread context, and you're trying to set a different ClientId using this method
     *
     * Use cases: CWM Following code when some activity should be executed for another client,
     * or accessing some Host's IDE subsystems like settings from a controller.
     * Otherwise, use ordinary [withClientId] to avoid hiding issues with lost/extra overridden ClientId
     *
     * **Note 2** Don't do this method inline!
     * If it's inline it's possible to do suspend calls inside [action], and thus it makes [withClientId] itself a suspend method.
     *
     * It may lead to unexpected behavior:
     * the method enters and sets [clientId] to the thread-local storage of a particular dispatcher's thread.
     * Then suspension is happening for some time, and during this suspension some other events can be processed on the same dispatcher's thread.
     * Since the thread local wasn't reset (due to the fact that the [withClientId] call is not finished yet) some event can observe this hanging [clientId],
     * which in essence should not be set for this event.
     */
    @Internal
    @JvmStatic
    fun <T> withExplicitClientId(clientId: ClientId?, action: () -> T): T {
      return withClientId(clientId, errorOnMismatch = false).use {
        action()
      }
    }

    /**
     * Computes a value under given [ClientId]
     *
     * **Note:** This method should not be called within a suspend context.
     * It is recommended to use `withContext(clientId.asContextElement())` instead.
     */
    @Internal
    @JvmStatic
    @RequiresBlockingContext
    fun withClientId(clientId: ClientId?): AccessToken {
      return withClientId(clientId, errorOnMismatch = true)
    }

    /**
     * The same as [withClientId] but it doesn't fire a log.error()
     * when there is a ClientId on the current thread context, and you're trying to set a different ClientId using this method
     *
     * Use cases: CWM Following code when some activity should be executed for another client,
     * or accessing some Host's IDE subsystems like settings from a controller.
     * Otherwise, use ordinary [withClientId] to avoid hiding issues with lost/extra overridden ClientId
     */
    @Internal
    @JvmStatic
    @RequiresBlockingContext
    fun withExplicitClientId(clientId: ClientId?): AccessToken {
      return withClientId(clientId, errorOnMismatch = false)
    }

    @Internal
    @JvmStatic
    @RequiresBlockingContext
    private fun withClientId(clientId: ClientId?, errorOnMismatch: Boolean): AccessToken {
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
    @Internal
    @JvmStatic
    @RequiresBlockingContext
    fun withClientId(clientIdValue: String): AccessToken {
      return withClientIdImpl(clientIdValue, errorOnMismatch = true)
    }

    /**
     * The same as [withClientId] but it doesn't fire a log.error()
     * when there is a ClientId on the current thread context, and you're trying to set a different ClientId using this method
     *
     * Use cases: CWM Following code when some activity should be executed for another client,
     * or accessing some Host's IDE subsystems like settings from a controller.
     * Otherwise, use ordinary [withClientId] to avoid hiding issues with lost/extra overridden ClientId
     */
    @Internal
    @JvmStatic
    @RequiresBlockingContext
    fun withExplicitClientId(clientIdValue: String): AccessToken {
      return withClientIdImpl(clientIdValue, errorOnMismatch = false)
    }

    private fun withClientIdImpl(clientIdValue: String, errorOnMismatch: Boolean): AccessToken {
      val oldClientId = currentThreadClientId ?: localId
      if (clientIdValue == oldClientId.value) {
        return AccessToken.EMPTY_ACCESS_TOKEN
      }

      val clientId = ClientId(clientIdValue)
      val newClientId = if (fakeLocalIds.contains(clientIdValue))
        localId
      else
        clientId

      val currentThreadContext = currentThreadContextOrNull()
      if (currentThreadContext == null) {
        return installThreadContext(ClientIdContextElement(newClientId))
      }

      val currentClientIdContextElement = currentThreadContext.clientIdContextElement
      val newContext = currentThreadContext + ClientIdContextElement(newClientId)
      if (errorOnMismatch) {
        if (currentClientIdContextElement != null && currentClientIdContextElement.clientId != newClientId) {
          logger.error(Throwable("Trying to set $newClientId, but it's already set to ${currentClientIdContextElement}. " +
                                 "Use 'withExplicitClientId' or 'withContext(clientId.asContextElement())' if you need to override ClientId").apply {
                                   currentClientIdContextElement.creationTrace?.let { addSuppressed(it) }
          })
        }
      }
      return installThreadContext(newContext, replace = true)
    }

    @Internal
    @ApiStatus.Obsolete
    @Deprecated(message = "Resolve ClientSessionsManager via Application in client code", level = DeprecationLevel.ERROR,
                replaceWith = ReplaceWith("ApplicationManager.getApplication().serviceOrNull<ClientSessionsManager<*>>()"))
    fun getCachedService(): ClientSessionsManager<*>? {
      return ApplicationManager.getApplication().serviceOrNull<ClientSessionsManager<*>>()
    }

    @Deprecated("ClientId propagation is handled by context propagation. You don't need to do it manually. The method will be removed soon.")
    @Internal
    @JvmStatic
    fun <T> decorateFunction(action: () -> T): () -> T = action

    @Deprecated("ClientId propagation is handled by context propagation. You don't need to do it manually. The method will be removed soon.")
    @Internal
    @JvmStatic
    fun decorateRunnable(runnable: Runnable): Runnable = runnable

    @Deprecated("ClientId propagation is handled by context propagation. You don't need to do it manually. The method will be removed soon.")
    @Internal
    @JvmStatic
    fun <T> decorateCallable(callable: Callable<T>): Callable<T> = callable

    @Deprecated("ClientId propagation is handled by context propagation. You don't need to do it manually. The method will be removed soon.")
    @Internal
    @JvmStatic
    fun <T, R> decorateFunction(function: Function<T, R>): Function<T, R> = function

    @Deprecated("ClientId propagation is handled by context propagation. You don't need to do it manually. The method will be removed soon.")
    @Internal
    @JvmStatic
    fun <T, U> decorateBiConsumer(biConsumer: BiConsumer<T, U>): BiConsumer<T, U> = biConsumer

    @Deprecated("ClientId propagation is handled by context propagation. You don't need to do it manually. The method will be removed soon.")
    @Internal
    @JvmStatic
    fun <T> decorateProcessor(processor: Processor<T>): Processor<T> = processor

    @Internal
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

@Internal
fun CoroutineContext.clientId(): ClientId? = this[ClientIdContextElement.Key]?.clientId

val CoroutineContext.clientIdContextElement: ClientIdContextElement?
  @Internal
  get() = this[ClientIdContextElement.Key]

val currentThreadClientId: ClientId?
  @Internal
  get() = currentThreadContext().clientIdContextElement?.clientId
