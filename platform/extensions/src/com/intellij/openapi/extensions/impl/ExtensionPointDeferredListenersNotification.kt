// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.extensions.impl

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class ExtensionPointDeferredListenersNotification(
  val ep: ExtensionPointImpl<*>,
  val notify: Runnable,
)