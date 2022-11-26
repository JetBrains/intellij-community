// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.extensions

/**
 * See [ExtensionPointChangeListener]
 */
interface ExtensionPointListener<T> {
  companion object {
    private val EMPTY_ARRAY = arrayOfNulls<ExtensionPointListener<*>?>(0)

    fun <T> emptyArray(): Array<ExtensionPointListener<T>> {
      @Suppress("UNCHECKED_CAST")
      return EMPTY_ARRAY as Array<ExtensionPointListener<T>>
    }
  }

  fun extensionAdded(extension: T, pluginDescriptor: PluginDescriptor) {}

  fun extensionRemoved(extension: T, pluginDescriptor: PluginDescriptor) {}
}