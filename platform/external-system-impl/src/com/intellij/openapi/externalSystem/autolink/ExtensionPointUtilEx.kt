// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.autolink

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.createExtensionDisposable
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
internal suspend fun <Extension : Any> ExtensionPointName<Extension>.withEachExtensionSafeAsync(
  parentDisposable: Disposable,
  action: suspend (Extension, Disposable) -> Unit
) {
  for (extension in extensionList) {
    val extensionDisposable = createExtensionDisposable(extension, parentDisposable)
    runCatching { action(extension, extensionDisposable) }
      .getOrLogException(logger<ExtensionPointImpl<*>>())
  }
  addExtensionPointListener(object : ExtensionPointListener<Extension> {
    override fun extensionAdded(extension: Extension, pluginDescriptor: PluginDescriptor) {
      val extensionDisposable = createExtensionDisposable(extension, parentDisposable)
      @OptIn(DelicateCoroutinesApi::class)
      GlobalScope.launch(extensionDisposable) {
        runCatching { action(extension, extensionDisposable) }
          .getOrLogException(logger<ExtensionPointImpl<*>>())
      }
    }
  }, parentDisposable)
}
