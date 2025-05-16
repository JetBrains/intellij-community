// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")

package com.intellij.openapi.module.impl

import com.intellij.ide.plugins.ContainerDescriptor
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ComponentConfig
import com.intellij.openapi.components.impl.stores.IComponentStore
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.Module
import com.intellij.serviceContainer.ComponentManagerImpl
import com.intellij.serviceContainer.PrecomputedExtensionModel
import com.intellij.serviceContainer.emptyConstructorMethodType
import com.intellij.serviceContainer.findConstructorOrNull
import org.jetbrains.annotations.ApiStatus
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType

private val moduleMethodType = MethodType.methodType(Void.TYPE, Module::class.java)

private val LOG: Logger
  get() = logger<ModuleComponentManager>()

@ApiStatus.Internal
class ModuleComponentManager(parent: ComponentManagerImpl) : ComponentManagerImpl(parent) {
  lateinit var module: Module

  override fun <T : Any> findConstructorAndInstantiateClass(lookup: MethodHandles.Lookup, aClass: Class<T>): T {
    @Suppress("UNCHECKED_CAST")
    return (lookup.findConstructorOrNull(aClass, moduleMethodType)?.invoke(module)
            ?: lookup.findConstructorOrNull(aClass, emptyConstructorMethodType)?.invoke()
            ?: RuntimeException("Cannot find suitable constructor, expected (Module) or ()")) as T
  }

  override val supportedSignaturesOfLightServiceConstructors: List<MethodType> = java.util.List.of(
    moduleMethodType,
    emptyConstructorMethodType,
  )

  fun initForModule(module: Module) {
    this.module = module
    registerServiceInstance(serviceInterface = Module::class.java, instance = module, pluginDescriptor = fakeCorePluginDescriptor)
  }

  @ApiStatus.Internal
  override fun isComponentSuitable(componentConfig: ComponentConfig): Boolean {
    if (!super.isComponentSuitable(componentConfig)) {
      return false
    }

    val options = componentConfig.options
    if (options.isNullOrEmpty()) {
      return true
    }

    for (optionName in options.keys) {
      if (optionName == "workspace" || optionName == "overrides") {
        continue
      }

      // we cannot filter using module options because at this moment module file data could be not loaded
      val message = "Don't specify $optionName in the component registration," +
                    " transform component to service and implement your logic in your getInstance() method"
      if (ApplicationManager.getApplication().isUnitTestMode) {
        LOG.error(message)
      }
      else {
        LOG.warn(message)
      }
    }
    return true
  }

  override fun getContainerDescriptor(pluginDescriptor: IdeaPluginDescriptorImpl): ContainerDescriptor {
    return pluginDescriptor.moduleContainerDescriptor
  }

  override fun dispose() {
    runCatching {
      serviceIfCreated<IComponentStore>()?.release()
    }.getOrLogException(LOG)
    super.dispose()
  }

  override fun debugString(short: Boolean): String = if (short) javaClass.simpleName else super.debugString(short = false)

  // expose to call it via ModuleImpl
  @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
  public override fun createComponents() {
    super.createComponents()
  }

  override fun registerComponents(
    modules: List<IdeaPluginDescriptorImpl>,
    app: Application?,
    precomputedExtensionModel: PrecomputedExtensionModel?,
    listenerCallbacks: MutableList<in Runnable>?,
  ) {
    super.registerComponents(modules, app, precomputedExtensionModel, listenerCallbacks)
    if (modules.any { it.pluginId == PluginManagerCore.CORE_ID }) {
      unregisterComponent(DeprecatedModuleOptionManager::class.java)
    }
  }
}
