// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.extensions.impl

import com.intellij.openapi.extensions.ExtensionPoint
import com.intellij.openapi.util.IntellijInternalApi
import org.jetbrains.annotations.ApiStatus

@IntellijInternalApi
@ApiStatus.Internal
class ExtensionPointDeferredListenersNotification(
  val ep: ExtensionPointImpl<*>,
  val notify: Runnable,
)