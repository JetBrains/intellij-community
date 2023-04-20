// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.autolink

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.createExtensionDisposable
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.openapi.progress.ProcessCanceledException
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CancellationException

@ApiStatus.Internal
internal suspend fun <Extension : Any> ExtensionPointName<Extension>.forEachExtensionSafe(
  parentDisposable: Disposable,
  action: suspend (Extension, Disposable) -> Unit
) {
  extensionList.map {
    safeLaunch(it, parentDisposable, action)
  }.joinAll()
}

@ApiStatus.Internal
internal fun <Extension : Any> ExtensionPointName<Extension>.whenExtensionAdded(
  parentDisposable: Disposable,
  action: suspend (Extension, Disposable) -> Unit
) {
  addExtensionPointListener(object : ExtensionPointListener<Extension> {
    override fun extensionAdded(extension: Extension, pluginDescriptor: PluginDescriptor) {
      safeLaunch(extension, parentDisposable, action)
    }
  }, parentDisposable)
}

@ApiStatus.Internal
internal suspend fun <Extension : Any> ExtensionPointName<Extension>.withEachExtensionSafe(
  parentDisposable: Disposable,
  action: suspend (Extension, Disposable) -> Unit
) {
  forEachExtensionSafe(parentDisposable, action)
  whenExtensionAdded(parentDisposable, action)
}

private fun <Extension : Any> ExtensionPointName<Extension>.safeLaunch(
  extension: Extension,
  parentDisposable: Disposable,
  action: suspend (Extension, Disposable) -> Unit
): Job {
  val extensionDisposable = createExtensionDisposable(extension, parentDisposable)
  return launch(extensionDisposable) {
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
      logger<ExtensionPointImpl<*>>().error(e)
    }
  }
}
