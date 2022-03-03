// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.extensions.impl

import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.extensions.LoadingOrder
import com.intellij.openapi.extensions.PluginDescriptor
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
abstract class ExtensionComponentAdapter internal constructor(implementationClassName: String,
                                                              @JvmField val pluginDescriptor: PluginDescriptor,
                                                              private val orderId: String?,
                                                              private val order: LoadingOrder,
                                                              @JvmField internal val implementationClassResolver: ImplementationClassResolver) : LoadingOrder.Orderable {
  companion object {
    @JvmField
    val EMPTY_ARRAY = arrayOfNulls<ExtensionComponentAdapter>(0)
  }

  // Class or String
  @JvmField
  internal var implementationClassOrName: Any = implementationClassName

  internal abstract val isInstanceCreated: Boolean

  abstract fun <T : Any> createInstance(componentManager: ComponentManager): T?

  override fun getOrder() = order

  override fun getOrderId() = orderId

  @Throws(ClassNotFoundException::class)
  fun <T> getImplementationClass(componentManager: ComponentManager): Class<T> {
    @Suppress("UNCHECKED_CAST")
    return implementationClassResolver.resolveImplementationClass(componentManager, this) as Class<T>
  }

  // used externally - cannot be package-local
  val assignableToClassName: String
    get() {
      val implementationClassOrName = implementationClassOrName
      return if (implementationClassOrName is String) implementationClassOrName else (implementationClassOrName as Class<*>).name
    }

  override fun toString(): String {
    return javaClass.simpleName + "(implementation=" + assignableToClassName + ", plugin=" + pluginDescriptor + ")"
  }
}