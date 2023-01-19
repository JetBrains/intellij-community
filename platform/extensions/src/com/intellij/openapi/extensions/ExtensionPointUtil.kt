// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.extensions

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.Disposer
import java.util.concurrent.CancellationException

fun <T : Any> ExtensionPointName<T>.withEachExtensionSafe(parentDisposable: Disposable?, action: (T, Disposable) -> Unit) {
  forEachExtensionSafe(parentDisposable, action)
  whenExtensionAdded(parentDisposable, action)
}

fun <T : Any> ExtensionPointName<T>.createExtensionDisposable(extension: T, parentDisposable: Disposable?): Disposable {
  val extensionDisposable = ExtensionPointUtil.createExtensionDisposable(extension, this)
  if (parentDisposable != null) {
    Disposer.register(parentDisposable, extensionDisposable)
  }
  return extensionDisposable
}

fun <T : Any> ExtensionPointName<T>.whenExtensionAdded(parentDisposable: Disposable?, action: (T, Disposable) -> Unit) {
  addExtensionPointListener(object : ExtensionPointListener<T> {
    override fun extensionAdded(extension: T, pluginDescriptor: PluginDescriptor) {
      val extensionDisposable = createExtensionDisposable(extension, parentDisposable)
      action(extension, extensionDisposable)
    }
  }, parentDisposable)
}

inline fun <T : Any> ExtensionPointName<T>.forEachExtensionSafe(
  parentDisposable: Disposable?,
  action: (T, Disposable) -> Unit
) {
  val logger = Logger.getInstance(ExtensionPointImpl::class.java)
  for (extension in extensionList) {
    val extensionDisposable = createExtensionDisposable(extension, parentDisposable)
    try {
      action(extension, extensionDisposable)
    }
    catch (e: ProcessCanceledException) {
      throw e
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Throwable) {
      logger.error(e)
    }
  }
}
