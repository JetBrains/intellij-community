// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.module.impl

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.Disposable
import com.intellij.openapi.client.ClientKind
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.components.DelegatingComponentManagerEx
import com.intellij.openapi.components.ServiceDescriptor
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.serviceContainer.AlreadyDisposedException
import com.intellij.serviceContainer.ComponentManagerImpl
import com.intellij.serviceContainer.PrecomputedExtensionModel
import com.intellij.serviceContainer.isEmpty
import com.intellij.serviceContainer.precomputeModuleLevelExtensionModel
import com.intellij.util.messages.MessageBus
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleBridgeImpl
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

private val LOG: Logger
  get() = logger<LazyModuleComponentManager>()

/**
 * The component manager of a module. It creates a [ModuleComponentManager] only when a plugin declares a module-level element,
 * or when a caller needs the container, for example, to register a service.
 *
 * The module identity stays here, so the container is not needed for it: this object is the module node in the [Disposer] tree,
 * and it holds the user data and the disposed state.
 *
 * The container is not a node in the [Disposer] tree. This object starts the disposal of the container in [beforeTreeDispose]
 * and completes it in [dispose]. Thus, the children of the module are disposed before the module services,
 * and a module without children has no node in the tree.
 */
@ApiStatus.Internal
class LazyModuleComponentManager(private val parent: ComponentManagerImpl) : UserDataHolderBase(), DelegatingComponentManagerEx,
                                                                             Disposable.Parent {
  private val lock = Any()

  private var module: Module? = null

  @Volatile
  private var container: ModuleComponentManager? = null

  // guarded by lock
  private var isMarkedAsCreated = false

  @Volatile
  private var isDisposeStarted = false

  @TestOnly
  fun getModuleName(): @NlsSafe String = module!!.name

  fun initForModule(module: Module) {
    this.module = module
  }

  val containerIfCreated: ModuleComponentManager?
    get() = container

  internal fun initContainer(precomputedExtensionModel: PrecomputedExtensionModel) {
    if (!precomputedExtensionModel.isEmpty) {
      synchronized(lock) {
        createContainer(precomputedExtensionModel)
      }
    }
  }

  fun markContainerAsCreated() {
    synchronized(lock) {
      isMarkedAsCreated = true
      container?.markContainerAsCreated()
    }
  }

  override val delegateComponentManager: ComponentManagerEx
    get() = container ?: synchronized(lock) { container ?: createContainer(precomputeModuleLevelExtensionModel()) }

  // call it under the lock
  private fun createContainer(precomputedExtensionModel: PrecomputedExtensionModel): ModuleComponentManager {
    container?.let { return it }
    if (isDisposeStarted) {
      ProgressManager.checkCanceled()
      throw AlreadyDisposedException("Already disposed: $this")
    }

    val result = ModuleComponentManager(parent)
    result.initForModule(module!!)
    result.initModuleContainer(precomputedExtensionModel)
    if (isMarkedAsCreated) {
      result.markContainerAsCreated()
    }
    container = result
    return result
  }

  override fun getMutableComponentContainerIfCreated(): ComponentManager? = container

  override fun getDisposableDelegate(): Disposable = this

  override fun isDisposed(): Boolean = isDisposeStarted

  override fun getDisposed(): Condition<*> = Condition<Any?> { isDisposeStarted }

  override fun <T : Any?> getService(serviceClass: Class<T?>): T? {
    val container = getMutableComponentContainerIfCreated() ?: return getProjectService(serviceClass, parent, createIfNeeded = true)
    return container.getService(serviceClass)
  }

  override fun <T : Any?> getServiceIfCreated(serviceClass: Class<T?>): T? {
    val container = getMutableComponentContainerIfCreated() ?: return getProjectService(serviceClass, parent, createIfNeeded = false)
    return container.getServiceIfCreated(serviceClass)
  }

  // a module container asks the project container on a miss and logs an error, so keep that without a container
  private fun <T> getProjectService(serviceClass: Class<T?>, project: ComponentManager, createIfNeeded: Boolean): T? {
    val service = if (createIfNeeded) project.getService(serviceClass) else project.getServiceIfCreated(serviceClass)
    if (service != null) {
      LOG.error("${serviceClass.name} is registered as project service, but requested as module one")
    }
    return service
  }

  override fun <T : Any?> getServices(serviceClass: Class<T?>, client: ClientKind?): List<T?> {
    return getMutableComponentContainerIfCreated()?.getServices(serviceClass, client) ?: emptyList()
  }

  override fun unloadServices(module: IdeaPluginDescriptor, services: List<ServiceDescriptor>) {
    container?.unloadServices(module, services)
  }

  override fun stopServicePreloading() {
    container?.stopServicePreloading()
  }

  @Deprecated("Deprecated in Java")
  override fun getMessageBus(): MessageBus {
    return container?.messageBus ?: throw IllegalArgumentException("Do not use module level message bus")
  }

  @Suppress("UsagesOfObsoleteApi")
  override fun getCoroutineScope(): CoroutineScope {
    return container?.getCoroutineScope() ?: throw RuntimeException("Module doesn't have coroutineScope")
  }

  override fun debugString(): String = container?.debugString() ?: "${javaClass.simpleName}@${System.identityHashCode(this)}"

  override fun beforeTreeDispose() {
    markDisposeStarted()?.beforeTreeDispose()
  }

  override fun dispose() {
    val container = markDisposeStarted()
    if (container == null) {
      runCatching {
        (module as ModuleBridgeImpl).resetModuleStore()
      }.getOrLogException(LOG)
    }
    else {
      // the container resets the store of the module
      Disposer.dispose(container)
    }
  }

  // the lock makes createContainer either publish the container before this call, or fail after it
  private fun markDisposeStarted(): ModuleComponentManager? {
    synchronized(lock) {
      isDisposeStarted = true
      return container
    }
  }
}
