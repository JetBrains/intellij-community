// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.extensions.impl

import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.extensions.*
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.util.XmlElement
import com.intellij.util.xmlb.XmlSerializer
import java.util.*

internal open class XmlExtensionAdapter(implementationClassName: String,
                                        pluginDescriptor: PluginDescriptor,
                                        orderId: String?,
                                        order: LoadingOrder,
                                        private var extensionElement: XmlElement?,
                                        implementationClassResolver: ImplementationClassResolver) : ExtensionComponentAdapter(
  implementationClassName, pluginDescriptor, orderId, order, implementationClassResolver) {
  companion object {
    private val NOT_APPLICABLE = Any()
  }

  @Volatile
  private var extensionInstance: Any? = null
  private var initializing = false

  override val isInstanceCreated: Boolean
    get() = extensionInstance != null

  override fun <T : Any> createInstance(componentManager: ComponentManager): T? {
    @Suppress("UNCHECKED_CAST")
    val instance = extensionInstance as T?
    return if (instance == null) doCreateInstance(componentManager) else instance.takeIf { it !== NOT_APPLICABLE }
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

  internal class SimpleConstructorInjectionAdapter(implementationClassName: String,
                                                   pluginDescriptor: PluginDescriptor,
                                                   descriptor: ExtensionDescriptor,
                                                   implementationClassResolver: ImplementationClassResolver) : XmlExtensionAdapter(
    implementationClassName, pluginDescriptor, descriptor.orderId, descriptor.order, descriptor.element, implementationClassResolver) {
    override fun <T> instantiateClass(aClass: Class<T>, componentManager: ComponentManager): T {
      if (aClass.name != "org.jetbrains.kotlin.asJava.finder.JavaElementFinder") {
        try {
          return super.instantiateClass(aClass, componentManager)
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
          ExtensionPointImpl.LOG.error(
            "Cannot create extension without pico container (class=" + aClass.name + ", constructors=" +
            Arrays.toString(aClass.declaredConstructors) + ")," +
            " please remove extra constructor parameters", e)
        }
      }
      return componentManager.instantiateClassWithConstructorInjection(aClass, aClass, pluginDescriptor.pluginId)
    }
  }
}