// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Internal

package com.intellij.openapi.extensions

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import org.jetbrains.annotations.ApiStatus.Internal

fun <T : Any> ExtensionPointName<T>.createExtensionDisposable(extension: T, parentDisposable: Disposable?): Disposable {
  val extensionDisposable = ExtensionPointUtil.createExtensionDisposable(extension, this)
  if (parentDisposable != null) {
    Disposer.register(parentDisposable, extensionDisposable)
  }
  return extensionDisposable
}
