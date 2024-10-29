// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.autolink

import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import org.jetbrains.annotations.ApiStatus


@ApiStatus.Internal
inline fun <Extension : Any> ExtensionPointName<Extension>.forEachExtensionSafeAsync(
  action: (Extension) -> Unit
) {
  for (extension in extensionList) {
    runCatching { action(extension) }
      .getOrLogException(logger<ExtensionPointImpl<*>>())
  }
}