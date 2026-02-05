// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.intellij

import com.intellij.ide.PowerSaveMode
import com.intellij.openapi.components.Service
import com.intellij.platform.icons.DeferredIcon
import com.intellij.platform.icons.impl.DeferredIconResolverService
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Service(Service.Level.APP)
class IntelliJDeferredIconResolverService(scope: CoroutineScope): DeferredIconResolverService(scope) {
  override fun scheduleEvaluation(icon: DeferredIcon) {
    if (!PowerSaveMode.isEnabled()) {
      super.scheduleEvaluation(icon)
    }
  }
}