// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.rpc.backend.impl

import com.intellij.diagnostic.PluginException
import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.extensions.LazyExtension
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.platform.rpc.RemoteApiProviderService
import com.intellij.platform.rpc.backend.RemoteApiProvider
import com.intellij.platform.rpc.backend.RemoteApiRegistration
import com.intellij.platform.rpc.lite.LiteRemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.RemoteApiDescriptor
import fleet.rpc.core.InstanceId
import fleet.rpc.server.RpcServiceLocator
import fleet.rpc.server.ServiceImplementation
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves a remote API implementation by the API FQN.
 *
 * The registry indexes the declarations of [RemoteApiProvider.EP_NAME] and [RemoteApiRegistration.EP_NAME] by the API FQN
 * without loading a class. A provider is constructed, and its implementations are created, on the first call to one of its APIs.
 * A provider declared without the `apiInterfaces` attribute is constructed on the first call to an API that no declaration names.
 */
internal class RemoteApiRegistry(coroutineScope: CoroutineScope) : RemoteApiProviderService, RpcServiceLocator {
  /** The created implementations. */
  private val remoteApis = ConcurrentHashMap<String, ServiceImplementation>()

  private val lock = Any()

  @Volatile
  private var index = buildIndex()

  /** The providers that ran `remoteApis()`. Guarded by [lock]. */
  private val materializedProviders = ArrayList<MaterializedProvider>()

  /** The bean registrations that created their implementation. Guarded by [lock]. */
  private val materializedRegistrations = ArrayList<RemoteApiRegistration>()

  init {
    RemoteApiProvider.EP_NAME.addChangeListener(coroutineScope) { onExtensionsChanged() }
    RemoteApiRegistration.EP_NAME.addChangeListener(coroutineScope) { onExtensionsChanged() }
  }

  private class Index(
    /** The API FQN to the provider that declares it in `apiInterfaces`. */
    @JvmField val providers: Map<String, LazyExtension<RemoteApiProvider>>,
    /** The API FQN to the bean registration that declares it in `apiInterface`. */
    @JvmField val registrations: Map<String, RemoteApiRegistration>,
    /** The providers without the `apiInterfaces` attribute. Only an instance knows their APIs. */
    @JvmField val unattributedProviders: List<LazyExtension<RemoteApiProvider>>,
  )

  private class MaterializedProvider(
    @JvmField val pluginDescriptor: PluginDescriptor,
    @JvmField val implementationClassName: String,
    @JvmField val registeredApis: Set<String>,
  ) {
    fun matches(extension: LazyExtension<RemoteApiProvider>): Boolean {
      return pluginDescriptor === extension.pluginDescriptor && implementationClassName == extension.implementationClassName
    }
  }

  private fun buildIndex(): Index {
    val providers = HashMap<String, LazyExtension<RemoteApiProvider>>()
    val unattributedProviders = ArrayList<LazyExtension<RemoteApiProvider>>()
    for (extension in RemoteApiProvider.EP_NAME.filterableLazySequence()) {
      val declaredApis = extension.getCustomAttribute(API_INTERFACES_ATTRIBUTE)
      if (declaredApis == null) {
        unattributedProviders.add(extension)
        continue
      }
      for (apiFqn in parseApiInterfaces(declaredApis)) {
        val previous = providers.putIfAbsent(apiFqn, extension)
        if (previous != null) {
          warnDuplicateDeclaration(apiFqn, previous.implementationClassName, extension.implementationClassName)
        }
      }
    }

    val registrations = HashMap<String, RemoteApiRegistration>()
    for (registration in RemoteApiRegistration.EP_NAME.extensionList) {
      val apiFqn = registration.apiFqn
      val previous = providers.get(apiFqn)?.implementationClassName ?: registrations.putIfAbsent(apiFqn, registration)?.implementationClass
      if (previous != null) {
        warnDuplicateDeclaration(apiFqn, previous, registration.implementationClass)
      }
    }
    return Index(providers = providers, registrations = registrations, unattributedProviders = unattributedProviders)
  }

  private fun warnDuplicateDeclaration(apiFqn: String, first: String, second: String) {
    LOG.warn(
      "Remote API '$apiFqn' is declared by both '$first' and '$second'. Each remote api should be registered exactly once: " +
      "either via the 'com.intellij.platform.rpc.backend.remoteApi' extension point or a " +
      "'${RemoteApiProvider::class.java.simpleName}', not both and not more than once."
    )
  }

  private fun onExtensionsChanged() {
    synchronized(lock) {
      val currentProviders = RemoteApiProvider.EP_NAME.filterableLazySequence().toList()
      val providerIterator = materializedProviders.iterator()
      while (providerIterator.hasNext()) {
        val materialized = providerIterator.next()
        if (currentProviders.none { materialized.matches(it) }) {
          providerIterator.remove()
          for (apiFqn in materialized.registeredApis) {
            unregisterRemoteApi(apiFqn)
          }
        }
      }

      val currentRegistrations = RemoteApiRegistration.EP_NAME.extensionList
      val registrationIterator = materializedRegistrations.iterator()
      while (registrationIterator.hasNext()) {
        val materialized = registrationIterator.next()
        if (currentRegistrations.none { it === materialized }) {
          registrationIterator.remove()
          unregisterRemoteApi(materialized.apiFqn)
        }
      }

      index = buildIndex()
    }
  }

  private fun registerRemoteApi(apiDescriptor: RemoteApiDescriptor<*>, apiImplementation: RemoteApi<*>) {
    val apiFqn = apiDescriptor.getApiFqn()
    LOG.debug("Registering remote api $apiFqn - $apiDescriptor")

    val serviceImplementation = ServiceImplementation(apiDescriptor, apiImplementation, serviceScope = null)

    val previous = remoteApis.putIfAbsent(apiFqn, serviceImplementation)
    if (previous != null) {
      LOG.warn(
        "Remote API '$apiFqn' is already registered. Each remote api should be registered exactly once: " +
        "either via the 'com.intellij.platform.rpc.backend.remoteApi' extension point or a " +
        "'${RemoteApiProvider::class.java.simpleName}', not both and not more than once."
      )
    }
  }

  private fun unregisterRemoteApi(apiFqn: String) {
    LOG.debug("Unregistering remote api $apiFqn")
    remoteApis.remove(apiFqn)
  }

  private fun resolveImplementation(apiFqn: String): ServiceImplementation? {
    remoteApis.get(apiFqn)?.let { return it }
    synchronized(lock) {
      remoteApis.get(apiFqn)?.let { return it }
      val index = index
      val provider = index.providers.get(apiFqn)
      if (provider != null) {
        materialize(provider, declaredApis = parseApiInterfaces(provider.getCustomAttribute(API_INTERFACES_ATTRIBUTE)!!))
        return remoteApis.get(apiFqn)
      }
      val registration = index.registrations.get(apiFqn)
      if (registration != null) {
        materialize(registration)
        return remoteApis.get(apiFqn)
      }
      for (extension in index.unattributedProviders) {
        materialize(extension, declaredApis = null)
      }
      return remoteApis.get(apiFqn)
    }
  }

  /** Runs `remoteApis()` of the provider once. Guarded by [lock]. */
  private fun materialize(extension: LazyExtension<RemoteApiProvider>, declaredApis: Set<String>?) {
    if (materializedProviders.any { it.matches(extension) }) {
      return
    }

    val registeredApis = LinkedHashSet<String>()
    // recorded before the provider runs, so a failing provider is not constructed again on every call
    materializedProviders.add(MaterializedProvider(extension.pluginDescriptor, extension.implementationClassName, registeredApis))
    LOG.debug("Processing remote api provider extension - ${extension.implementationClassName}")
    val provider = extension.instance ?: return
    val sink = object : RemoteApiProvider.Sink {
      override fun <T : RemoteApi<Unit>> remoteApi(descriptor: RemoteApiDescriptor<T>, implementation: () -> T) {
        registeredApis.add(descriptor.getApiFqn())
        registerRemoteApi(apiDescriptor = descriptor, apiImplementation = implementation())
      }
    }
    try {
      with(provider) {
        sink.remoteApis()
      }
    }
    catch (e: Throwable) {
      rethrowControlFlowException(e)
      LOG.error(PluginException("Remote api provider ${extension.implementationClassName} failed", e, extension.pluginDescriptor.pluginId))
      return
    }

    if (declaredApis != null && declaredApis != registeredApis) {
      LOG.error(PluginException(
        "Remote api provider ${extension.implementationClassName} declares $API_INTERFACES_ATTRIBUTE=${declaredApis.sorted()} " +
        "but registers ${registeredApis.sorted()}. Fix the attribute in the plugin descriptor.",
        extension.pluginDescriptor.pluginId,
      ))
    }
  }

  /** Creates the implementation of the bean registration once. Guarded by [lock]. */
  private fun materialize(registration: RemoteApiRegistration) {
    if (materializedRegistrations.any { it === registration }) {
      return
    }
    materializedRegistrations.add(registration)
    registerRemoteApi(
      apiDescriptor = remoteApiDescriptorOf(registration.loadApiInterface()),
      apiImplementation = registration.createImplementation()
    )
  }

  fun <T : RemoteApi<Unit>> tryResolve(descriptor: RemoteApiDescriptor<T>): T? {
    @Suppress("UNCHECKED_CAST")
    return resolveImplementation(descriptor.getApiFqn())?.instance as? T
  }

  override suspend fun <T : RemoteApi<Unit>> resolve(descriptor: RemoteApiDescriptor<T>): T {
    return tryResolve(descriptor) ?: throw IllegalStateException("No remote API found for $descriptor")
  }

  override fun resolve(serviceId: InstanceId): ServiceImplementation? {
    return resolveImplementation(serviceId.id).also {
      if (it == null) {
        LOG.debug("No remote API found for service ID: ${serviceId.id}")
        LOG.trace { "Available remote APIs: ${listRegisteredApis().joinToString("\n\t")}" }
      }
    }
  }

  /** The declared and the created APIs. */
  override fun listRegisteredApis(): List<String> {
    val index = index
    return (index.providers.keys + index.registrations.keys + remoteApis.keys).toList()
  }

  override fun isServiceOperational(): Boolean {
    return true
  }

  companion object {
    private val LOG = logger<RemoteApiRegistry>()

    /** The extension attribute that lists the API interfaces a provider registers. */
    const val API_INTERFACES_ATTRIBUTE: String = "apiInterfaces"

    /** Splits the attribute value into the Kotlin FQNs, in the form [RemoteApiDescriptor.getApiFqn] returns. */
    fun parseApiInterfaces(value: String): Set<String> {
      return value.splitToSequence(',').map { it.trim().replace('$', '.') }.filterTo(LinkedHashSet()) { it.isNotEmpty() }
    }
  }
}

/** The FQN in the form [RemoteApiDescriptor.getApiFqn] returns: a nested interface is `Outer.Inner`. */
private val RemoteApiRegistration.apiFqn: String
  get() = apiInterface.replace('$', '.')

internal class LiteRemoteApiRegistry : LiteRemoteApiProviderService {
  override fun isConnected(): Boolean {
    return true
  }

  override fun <T : RemoteApi<Unit>> tryResolve(descriptor: RemoteApiDescriptor<T>): T? {
    val service = service<RemoteApiProviderService>() as RemoteApiRegistry
    return service.tryResolve(descriptor)
  }

  override suspend fun <T : RemoteApi<Unit>> awaitConnectionAndResolve(descriptor: RemoteApiDescriptor<T>): T {
    return serviceAsync<RemoteApiProviderService>().resolve(descriptor)
  }
}
