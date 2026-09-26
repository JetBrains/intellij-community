// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Internal

package com.intellij.openapi.extensions

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal

fun <T : Any> ExtensionPointName<T>.createExtensionDisposable(extension: T, parentDisposable: Disposable): Disposable {
  return ExtensionPointUtil.createExtensionDisposable(extension, this)
    .also { Disposer.register(parentDisposable, it) }
}

inline fun <E : Any> ExtensionPointName<E>.forEachExtensionSafeInline(handler: (E) -> Unit) {
  extensionList.forEach {
    runCatching { handler(it) }
      .getOrLogException(logger<ExtensionPointImpl<*>>())
  }
}

@Deprecated("Pass CoroutineScope to addExtensionPointListener")
fun <E : Any> ExtensionPointName<E>.withEachExtensionSafe(parentDisposable: Disposable, handler: (E) -> Unit) {
  forEachExtensionSafe(handler)
  addExtensionPointListener(object : ExtensionPointListener<E> {
    override fun extensionAdded(extension: E, pluginDescriptor: PluginDescriptor) {
      runCatching { handler(extension) }
        .getOrLogException(logger<ExtensionPointImpl<*>>())
    }
  }, parentDisposable)
}

fun <E : Any> ExtensionPointName<E>.withEachExtensionSafe(
  coroutineScope: CoroutineScope,
  handler: (E) -> Unit,
) {
  forEachExtensionSafe(handler)
  addExtensionPointListener(coroutineScope, object : ExtensionPointListener<E> {
    override fun extensionAdded(extension: E, pluginDescriptor: PluginDescriptor) {
      runCatching { handler(extension) }
        .getOrLogException(logger<ExtensionPointImpl<*>>())
    }
  })
}

@ApiStatus.Experimental
fun <E : Any> ExtensionPointName<E>.trackEachExtensionSafe(
  coroutineScope: CoroutineScope,
  handler: (extension: E, extensionScope: CoroutineScope) -> Unit,
) {
  val extensionScopes = mutableMapOf<E, CoroutineScope>()

  getRootPoint().addExtensionPointListener(coroutineScope, invokeForLoadedExtensions = true, object : ExtensionPointListener<E> {
    override fun extensionAdded(extension: E, pluginDescriptor: PluginDescriptor) {
      runCatching {
        val extensionScope = coroutineScope.childScope(extension.javaClass.name, supervisor = true)
        extensionScopes[extension] = extensionScope
        handler(extension, extensionScope)
      }
        .getOrLogException(logger<ExtensionPointImpl<*>>())
    }

    override fun extensionRemoved(extension: E, pluginDescriptor: PluginDescriptor) {
      val oldScope = extensionScopes.remove(extension)
      if (oldScope != null) {
        oldScope.cancel()
      }
      else {
        logger<ExtensionPointImpl<*>>().error("trackEachExtensionSafe: Extension $extension was removed without being registered")
      }
    }
  })
}
