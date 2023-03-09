// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.extensions

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer

fun <T : Any> ExtensionPointName<T>.withEachExtensionSafe(action: (T, Disposable) -> Unit) = withEachExtensionSafe(null, action)
fun <T : Any> ExtensionPointName<T>.withEachExtensionSafe(parentDisposable: Disposable?, action: (T, Disposable) -> Unit) {
  forEachExtensionSafe(parentDisposable, action)
  whenExtensionAdded(parentDisposable, action)
}

fun <T : Any> ExtensionPointName<T>.createExtensionDisposable(extension: T) = createExtensionDisposable(extension, null)
fun <T : Any> ExtensionPointName<T>.createExtensionDisposable(extension: T, parentDisposable: Disposable?): Disposable {
  val extensionDisposable = ExtensionPointUtil.createExtensionDisposable(extension, this)
  if (parentDisposable != null) {
    Disposer.register(parentDisposable, extensionDisposable)
  }
  return extensionDisposable
}

fun <T : Any> ExtensionPointName<T>.forEachExtensionSafe(action: (T, Disposable) -> Unit) = forEachExtensionSafe(null, action)
fun <T : Any> ExtensionPointName<T>.forEachExtensionSafe(parentDisposable: Disposable?, action: (T, Disposable) -> Unit) {
  forEachExtensionSafe { extension ->
    val extensionDisposable = createExtensionDisposable(extension, parentDisposable)
    action(extension, extensionDisposable)
  }
}

fun <T : Any> ExtensionPointName<T>.whenExtensionAdded(action: (T, Disposable) -> Unit) = whenExtensionAdded(null, action)
fun <T : Any> ExtensionPointName<T>.whenExtensionAdded(parentDisposable: Disposable?, action: (T, Disposable) -> Unit) {
  addExtensionPointListener(object : ExtensionPointListener<T> {
    override fun extensionAdded(extension: T, pluginDescriptor: PluginDescriptor) {
      val extensionDisposable = createExtensionDisposable(extension, parentDisposable)
      action(extension, extensionDisposable)
    }
  }, parentDisposable)
}
