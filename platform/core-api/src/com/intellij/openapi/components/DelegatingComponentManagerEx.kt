// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.components

import com.intellij.diagnostic.ActivityCategory
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.Disposable
import com.intellij.openapi.client.ClientKind
import com.intellij.openapi.extensions.ExtensionsArea
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.Condition
import com.intellij.util.messages.MessageBus
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import org.picocontainer.ComponentAdapter

@ApiStatus.Internal
interface DelegatingComponentManagerEx: ComponentManagerEx {

  @get:ApiStatus.Internal
  val delegateComponentManager: ComponentManagerEx

  @ApiStatus.Internal
  override suspend fun <T : Any> getServiceAsync(keyClass: Class<T>): T =
    delegateComponentManager.getServiceAsync(keyClass)

  @ApiStatus.Internal
  override suspend fun <T : Any> getServiceAsyncIfDefined(keyClass: Class<T>): T? =
    delegateComponentManager.getServiceAsyncIfDefined(keyClass)

  @ApiStatus.Internal
  override fun getCoroutineScope(): CoroutineScope =
    delegateComponentManager.getCoroutineScope()

  @ApiStatus.Internal
  override fun getMutableComponentContainer(): ComponentManager =
    delegateComponentManager.getMutableComponentContainer()

  @ApiStatus.Internal
  override fun getDisposableDelegate(): Disposable =
    delegateComponentManager.getDisposableDelegate()

  @ApiStatus.Internal
  override fun instanceCoroutineScope(pluginClass: Class<*>): CoroutineScope =
    delegateComponentManager.instanceCoroutineScope(pluginClass)

  @ApiStatus.Internal
  override fun unregisterComponent(componentKey: Class<*>): ComponentAdapter? =
    delegateComponentManager.unregisterComponent(componentKey)

  @ApiStatus.Internal
  override fun <T : Any> replaceServiceInstance(serviceInterface: Class<T>, instance: T, parentDisposable: Disposable) =
    delegateComponentManager.replaceServiceInstance(serviceInterface, instance, parentDisposable)

  @ApiStatus.Internal
  override fun instances(createIfNeeded: Boolean, filter: ((Class<*>) -> Boolean)?): Sequence<Any> =
    delegateComponentManager.instances(createIfNeeded, filter)

  @ApiStatus.Internal
  override fun processAllImplementationClasses(processor: (Class<*>, PluginDescriptor?) -> Unit) {
    delegateComponentManager.processAllImplementationClasses(processor)
  }

  @ApiStatus.Internal
  override fun registerService(serviceInterface: Class<*>, implementation: Class<*>, pluginDescriptor: PluginDescriptor, override: Boolean, clientKind: ClientKind?) {
    delegateComponentManager.registerService(serviceInterface, implementation, pluginDescriptor, override, clientKind)
  }

  @ApiStatus.Internal
  override fun <T : Any> getServiceByClassName(serviceClassName: String): T? =
    delegateComponentManager.getServiceByClassName(serviceClassName)

  @ApiStatus.Internal
  override fun unloadServices(module: IdeaPluginDescriptor, services: List<ServiceDescriptor>) =
    delegateComponentManager.unloadServices(module, services)

  @ApiStatus.Internal
  override fun processAllHolders(processor: (String, Class<*>, PluginDescriptor?) -> Unit) {
    delegateComponentManager.processAllHolders(processor)
  }

  @ApiStatus.Internal
  override fun pluginCoroutineScope(pluginClassloader: ClassLoader): CoroutineScope =
    delegateComponentManager.pluginCoroutineScope(pluginClassloader)

  @ApiStatus.Internal
  override fun stopServicePreloading() {
    delegateComponentManager.stopServicePreloading()
  }

  @ApiStatus.Internal
  override fun <T : Any> collectInitializedComponents(aClass: Class<T>): List<T> =
    delegateComponentManager.collectInitializedComponents(aClass)

  @ApiStatus.Internal
  override fun debugString(): String =
    delegateComponentManager.debugString()

  @ApiStatus.Internal
  override fun isServiceSuitable(descriptor: ServiceDescriptor): Boolean =
    delegateComponentManager.isServiceSuitable(descriptor)

  @ApiStatus.Internal
  override fun <T : Any> registerServiceInstance(serviceInterface: Class<T>, instance: T, pluginDescriptor: PluginDescriptor) {
    delegateComponentManager.registerServiceInstance(serviceInterface, instance, pluginDescriptor)
  }

  @ApiStatus.Internal
  override fun getServiceImplementation(key: Class<*>): Class<*>? =
    delegateComponentManager.getServiceImplementation(key)

  @ApiStatus.Internal
  override fun <T : Any> replaceComponentInstance(componentKey: Class<T>, componentImplementation: T, parentDisposable: Disposable?) =
    delegateComponentManager.replaceComponentInstance(componentKey, componentImplementation, parentDisposable)

  @ApiStatus.Internal
  override fun registerComponentInstance(key: Class<*>, instance: Any) =
    delegateComponentManager.registerComponentInstance(key, instance)

  @ApiStatus.Internal
  override fun <T : Any> replaceRegularServiceInstance(serviceInterface: Class<T>, instance: T) {
    delegateComponentManager.replaceRegularServiceInstance(serviceInterface, instance)
  }

  @kotlin.Deprecated("Deprecated in interface")
  @ApiStatus.ScheduledForRemoval
  override fun getComponent(name: String): BaseComponent? =
    delegateComponentManager.getComponent(name)

  @kotlin.Deprecated("Deprecated in interface")
  @ApiStatus.ScheduledForRemoval
  override fun <T : Any?> getComponent(interfaceClass: Class<T?>): T? =
    delegateComponentManager.getComponent(interfaceClass)

  override fun hasComponent(interfaceClass: Class<*>): Boolean =
    delegateComponentManager.hasComponent(interfaceClass)

  @ApiStatus.Internal
  override fun isInjectionForExtensionSupported(): Boolean =
    delegateComponentManager.isInjectionForExtensionSupported

  override fun getMessageBus(): MessageBus =
    delegateComponentManager.messageBus

  override fun isDisposed(): Boolean =
    delegateComponentManager.isDisposed

  override fun getDisposed(): Condition<*> =
    delegateComponentManager.disposed

  override fun <T : Any?> getService(serviceClass: Class<T?>): T? =
    delegateComponentManager.getService(serviceClass)

  @ApiStatus.Internal
  override fun <T : Any?> getServices(serviceClass: Class<T?>, client: ClientKind?): List<T?> =
    delegateComponentManager.getServices(serviceClass, client)

  override fun <T : Any?> getServiceIfCreated(serviceClass: Class<T?>): T? =
    delegateComponentManager.getServiceIfCreated(serviceClass)

  override fun getExtensionArea(): ExtensionsArea =
    delegateComponentManager.extensionArea

  @ApiStatus.Internal
  override fun <T : Any?> instantiateClass(aClass: Class<T?>, pluginId: PluginId): T? =
    delegateComponentManager.instantiateClass(aClass, pluginId)

  @ApiStatus.Internal
  override fun <T : Any?> instantiateClass(className: String, pluginDescriptor: PluginDescriptor): T & Any =
    delegateComponentManager.instantiateClass(className, pluginDescriptor)

  @ApiStatus.Internal
  override fun <T : Any?> instantiateClassWithConstructorInjection(aClass: Class<T?>, key: Any, pluginId: PluginId): T? =
    delegateComponentManager.instantiateClassWithConstructorInjection(aClass, key, pluginId)

  @ApiStatus.Internal
  override fun logError(error: Throwable, pluginId: PluginId) {
    delegateComponentManager.logError(error, pluginId)
  }

  @ApiStatus.Internal
  override fun createError(error: Throwable, pluginId: PluginId): RuntimeException =
    delegateComponentManager.createError(error, pluginId)

  @ApiStatus.Internal
  override fun createError(message: @NonNls String, pluginId: PluginId): RuntimeException =
    delegateComponentManager.createError(message, pluginId)

  override fun createError(message: @NonNls String, error: Throwable?, pluginId: PluginId, attachments: Map<String?, String?>?): RuntimeException =
    delegateComponentManager.createError(message, error, pluginId, attachments)

  @ApiStatus.Internal
  override fun <T : Any?> loadClass(className: String, pluginDescriptor: PluginDescriptor): Class<T?> =
    delegateComponentManager.loadClass(className, pluginDescriptor)

  @ApiStatus.Internal
  override fun getActivityCategory(isExtension: Boolean): ActivityCategory =
    delegateComponentManager.getActivityCategory(isExtension)

  override fun unregisterService(serviceInterface: Class<*>) {
    delegateComponentManager.unregisterService(serviceInterface)
  }

  @ApiStatus.Internal
  override fun dispose() {
    delegateComponentManager.dispose()
  }
}