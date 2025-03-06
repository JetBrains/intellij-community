package com.intellij.workspaceModel.ide.impl.legacyBridge.module

import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.Application
import com.intellij.openapi.module.impl.ModuleComponentManager
import com.intellij.openapi.roots.TestModuleProperties
import com.intellij.serviceContainer.ComponentManagerImpl
import com.intellij.serviceContainer.PrecomputedExtensionModel
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots.TestModulePropertiesBridge
import com.intellij.openapi.module.Module
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class ModuleBridgeComponentManager(parent: ComponentManagerImpl) : ModuleComponentManager(parent) {
  override fun initForModule(module: Module) {
    super.initForModule(module)
    // This is a temporary solution and should be removed after full migration to [TestModulePropertiesBridge]
    val plugins = PluginManagerCore.getPluginSet().getEnabledModules()
    val corePluginDescriptor = plugins.find { it.pluginId == PluginManagerCore.CORE_ID }
                               ?: error("Core plugin with id: ${PluginManagerCore.CORE_ID} should be available")
    registerService(TestModuleProperties::class.java, TestModulePropertiesBridge::class.java, corePluginDescriptor, false)
  }

  override fun registerComponents(
    modules: List<IdeaPluginDescriptorImpl>,
    app: Application?,
    precomputedExtensionModel: PrecomputedExtensionModel?,
    listenerCallbacks: MutableList<in Runnable>?,
  ) {
    (module as ModuleBridgeImpl).registerComponents(
      corePlugin = modules.find { it.pluginId == PluginManagerCore.CORE_ID },
      modules = modules,
      precomputedExtensionModel = precomputedExtensionModel,
      app = app,
      listenerCallbacks = listenerCallbacks
    )
  }
  
  fun superRegisterComponents(
    modules: List<IdeaPluginDescriptorImpl>,
    app: Application?,
    precomputedExtensionModel: PrecomputedExtensionModel?,
    listenerCallbacks: MutableList<in Runnable>?,
  ) {
    super.registerComponents(modules, app, precomputedExtensionModel, listenerCallbacks)
  }
}