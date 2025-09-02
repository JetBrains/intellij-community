// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.components

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.objectTree.ReferenceDelegatingDisposableInternal
import com.intellij.openapi.client.ClientKind
import com.intellij.openapi.extensions.PluginDescriptor
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.picocontainer.ComponentAdapter

@ApiStatus.Internal
interface ComponentManagerEx : ComponentManager, ReferenceDelegatingDisposableInternal {
  @ApiStatus.Experimental
  @ApiStatus.Internal
  suspend fun <T : Any> getServiceAsync(keyClass: Class<T>): T {
    throw AbstractMethodError()
  }

  suspend fun <T : Any> getServiceAsyncIfDefined(keyClass: Class<T>): T? {
    throw AbstractMethodError()
  }

  @ApiStatus.Obsolete
  @ApiStatus.Internal
  fun getCoroutineScope(): CoroutineScope

  @ApiStatus.Internal
  fun getMutableComponentContainer(): ComponentManager =
    this as ComponentManager

  @ApiStatus.Internal
  override fun getDisposableDelegate(): Disposable = this

  @ApiStatus.Internal
  fun instanceCoroutineScope(pluginClass: Class<*>): CoroutineScope

  @ApiStatus.Internal
  fun unregisterComponent(componentKey: Class<*>): ComponentAdapter?

  @TestOnly
  @ApiStatus.Internal
  fun <T : Any> replaceServiceInstance(serviceInterface: Class<T>, instance: T, parentDisposable: Disposable)

  @ApiStatus.Internal
  fun instances(createIfNeeded: Boolean = false, filter: ((implClass: Class<*>) -> Boolean)? = null): Sequence<Any>

  @ApiStatus.Internal
  fun processAllImplementationClasses(processor: (componentClass: Class<*>, plugin: PluginDescriptor?) -> Unit)

  /**
   * Use only if approved by core team.
   */
  @ApiStatus.Internal
  fun registerService(
    serviceInterface: Class<*>,
    implementation: Class<*>,
    pluginDescriptor: PluginDescriptor,
    override: Boolean,
    clientKind: ClientKind? = null,
  )

  @ApiStatus.Internal
  fun <T : Any> getServiceByClassName(serviceClassName: String): T?

  @ApiStatus.Internal
  fun unloadServices(module: IdeaPluginDescriptor, services: List<ServiceDescriptor>)

  @ApiStatus.Internal
  fun processAllHolders(processor: (keyClass: String, componentClass: Class<*>, plugin: PluginDescriptor?) -> Unit)

  @ApiStatus.Internal
  fun pluginCoroutineScope(pluginClassloader: ClassLoader): CoroutineScope

  @ApiStatus.Internal
  fun stopServicePreloading()

  @ApiStatus.Internal
  fun <T : Any> collectInitializedComponents(aClass: Class<T>): List<T>

  @ApiStatus.Internal
  fun debugString(): String

  @ApiStatus.Internal
  fun isServiceSuitable(descriptor: ServiceDescriptor): Boolean

  @ApiStatus.Internal
  fun <T : Any> registerServiceInstance(
    serviceInterface: Class<T>,
    instance: T,
    @Suppress("UNUSED_PARAMETER") pluginDescriptor: PluginDescriptor,
  )

  @ApiStatus.Internal
  fun getServiceImplementation(key: Class<*>): Class<*>?

  @ApiStatus.Internal
  fun <T : Any> replaceComponentInstance(componentKey: Class<T>, componentImplementation: T, parentDisposable: Disposable?)

  @TestOnly
  @ApiStatus.Internal
  fun registerComponentInstance(key: Class<*>, instance: Any)

  @ApiStatus.Internal
  fun unregisterService(serviceInterface: Class<*>)

  @ApiStatus.Internal
  fun <T : Any> replaceRegularServiceInstance(serviceInterface: Class<T>, instance: T)
}