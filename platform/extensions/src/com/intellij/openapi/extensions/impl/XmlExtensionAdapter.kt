// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.extensions.impl

import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.*
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.util.xml.dom.XmlElement
import com.intellij.util.xmlb.XmlSerializer

private val NOT_APPLICABLE = Any()

internal open class XmlExtensionAdapter(implementationClassName: String,
                                        pluginDescriptor: PluginDescriptor,
                                        orderId: String?,
                                        order: LoadingOrder,
                                        private var extensionElement: XmlElement?,
                                        implementationClassResolver: ImplementationClassResolver)
  : ExtensionComponentAdapter(implementationClassName = implementationClassName,
                              pluginDescriptor = pluginDescriptor,
                              orderId = orderId,
                              order = order,
                              implementationClassResolver = implementationClassResolver) {
  @Volatile
  private var extensionInstance: Any? = null
  private var initializing = false

  override val isInstanceCreated: Boolean
    get() = extensionInstance != null

  override fun <T : Any> createInstance(componentManager: ComponentManager): T? {
    @Suppress("UNCHECKED_CAST")
    return (extensionInstance as T?)?.takeIf { it !== NOT_APPLICABLE } ?: doCreateInstance(componentManager)
  }

  @Synchronized
  private fun <T : Any> doCreateInstance(componentManager: ComponentManager): T? {
    @Suppress("UNCHECKED_CAST")
    var instance = extensionInstance as T?
    if (instance != null) {
      return instance.takeIf { it !== NOT_APPLICABLE }
    }

    if (initializing) {
      throw componentManager.createError("Cyclic extension initialization: $this", pluginDescriptor.pluginId)
    }

    try {
      initializing = true
      @Suppress("UNCHECKED_CAST")
      val aClass = implementationClassResolver.resolveImplementationClass(componentManager, this) as Class<T>
      instance = instantiateClass(aClass, componentManager)
      if (instance is PluginAware) {
        instance.setPluginDescriptor(pluginDescriptor)
      }
      val element = extensionElement
      if (element != null) {
        @Suppress("UsagesOfObsoleteApi")
        XmlSerializer.getBeanBinding(instance::class.java).deserializeInto(instance, element)
        extensionElement = null
      }
      extensionInstance = instance
    }
    catch (e: ExtensionNotApplicableException) {
      extensionInstance = NOT_APPLICABLE
      extensionElement = null
      return null
    }
    catch (e: ProcessCanceledException) {
      throw e
    }
    catch (e: Throwable) {
      throw componentManager.createError("Cannot create extension (class=$assignableToClassName)", e, pluginDescriptor.pluginId, null)
    }
    finally {
      initializing = false
    }
    return instance
  }

  protected open fun <T> instantiateClass(aClass: Class<T>, componentManager: ComponentManager): T {
    return componentManager.instantiateClass(aClass, pluginDescriptor.pluginId)
  }
}

internal class SimpleConstructorInjectionAdapter(
  implementationClassName: String,
  pluginDescriptor: PluginDescriptor,
  descriptor: ExtensionDescriptor,
  extensionElement: XmlElement?,
  implementationClassResolver: ImplementationClassResolver,
) : XmlExtensionAdapter(
  implementationClassName = implementationClassName,
  pluginDescriptor = pluginDescriptor,
  orderId = descriptor.orderId,
  order = descriptor.order,
  extensionElement = extensionElement,
  implementationClassResolver = implementationClassResolver,
) {
  override fun <T> instantiateClass(aClass: Class<T>, componentManager: ComponentManager): T {
    try {
      return componentManager.instantiateClass(aClass, pluginDescriptor.pluginId)
    }
    catch (e: ProcessCanceledException) {
      throw e
    }
    catch (e: ExtensionNotApplicableException) {
      throw e
    }
    catch (e: RuntimeException) {
      val cause = e.cause
      if (!(cause is NoSuchMethodException || cause is IllegalArgumentException)) {
        throw e
      }
      logger<ExtensionPointImpl<*>>().error(
        "Cannot create extension without pico container " +
        "(class=${aClass.name}, constructors=${aClass.declaredConstructors.contentToString()}), " +
        "please remove extra constructor parameters", e)
      return componentManager.instantiateClassWithConstructorInjection(aClass, aClass, pluginDescriptor.pluginId)
    }
  }
}