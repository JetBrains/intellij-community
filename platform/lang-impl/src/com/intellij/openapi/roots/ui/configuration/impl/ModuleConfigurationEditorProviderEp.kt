// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration.impl

import com.intellij.diagnostic.PluginException
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.extensions.PluginAware
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleConfigurationEditor
import com.intellij.openapi.roots.ui.configuration.ModuleConfigurationEditorProvider
import com.intellij.util.xmlb.annotations.Attribute
import org.jetbrains.annotations.ApiStatus
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType

/**
 * Implements [ModuleConfigurationEditorProvider] extension point supporting the deprecated way when an extension is instantiated with 
 * [Module] as a constructor parameter.
 * When this possibility is removed, the extension point can be converted to a regular 'interface' extension point.
 */
class ModuleConfigurationEditorProviderEp @ApiStatus.Internal constructor(): PluginAware {
  private var pluginDescriptor: PluginDescriptor? = null
  
  @Attribute("implementation")
  @JvmField
  var implementationClass: String? = null
  
  @Volatile
  private var cachedInstance: ModuleConfigurationEditorProvider? = null
  
  override fun setPluginDescriptor(pluginDescriptor: PluginDescriptor) {
    this.pluginDescriptor = pluginDescriptor
  }
  
  @ApiStatus.Internal
  fun getOrCreateInstance(module: Module): ModuleConfigurationEditorProvider {
    try {
      cachedInstance?.let { 
        return it 
      }
      
      val implementationClass = implementationClass ?: error("'implementation' attribute is not specified'")
      val pluginDescriptor = pluginDescriptor ?: error("plugin descriptor is not set")
      val instanceClass = ApplicationManager.getApplication().loadClass<ModuleConfigurationEditor>(implementationClass, pluginDescriptor)
      val lookup = MethodHandles.privateLookupIn(instanceClass, methodLookup)
      val defaultConstructor: MethodHandle
      try {
        defaultConstructor = lookup.findConstructor(instanceClass, emptyConstructorMethodType)
      }
      catch (_: NoSuchMethodException) {
        val constructorWithModule = lookup.findConstructor(instanceClass, moduleMethodType)
        LOG.error(PluginException("""
          |'moduleConfigurationEditorProvider' extensions must have a constructor without parameters and take Module instance from
          |the parameter of 'createEditors' function ('state.getCurrentRootModel().getModule()'), but '$implementationClass'
          |has a constructor taking 'Module' as parameter.
        """.trimMargin(), pluginDescriptor.pluginId))
        return constructorWithModule.invoke(module) as ModuleConfigurationEditorProvider
      }
      synchronized(this) {
        cachedInstance?.let {
          return it
        }
        val provider = defaultConstructor.invoke() as ModuleConfigurationEditorProvider
        cachedInstance = provider
        return provider
      }
    }
    catch (t: Throwable) {
      throw PluginException("Failed to instantiate ModuleConfigurationEditorProvider ($implementationClass)", t, pluginDescriptor?.pluginId)
    }
  }
}

private val LOG = fileLogger()
private val methodLookup = MethodHandles.lookup()
private val emptyConstructorMethodType: MethodType = MethodType.methodType(Void.TYPE)
private val moduleMethodType = MethodType.methodType(Void.TYPE, Module::class.java)

